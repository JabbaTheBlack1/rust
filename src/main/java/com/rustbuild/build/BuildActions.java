package com.rustbuild.build;

import com.rustbuild.block.BuildingBlock;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Серверные действия: постановка, улучшение, починка, урон, снос. Элемент обрабатывается ЦЕЛИКОМ. */
public final class BuildActions {
    /** 10 минут после постройки, как в Rust, можно сносить молотком. */
    public static final long DEMOLISH_WINDOW = 12000L;
    /** 30 секунд после получения урона чинить нельзя. */
    public static final long REPAIR_COOLDOWN = 600L;

    private BuildActions() {}

    public static int cost(PieceType type, Tier tier) {
        return type.costMultiplier * tier.unitCost;
    }

    private static void msg(ServerPlayer p, Component c) { p.displayClientMessage(c, true); }

    public static boolean isPieceBlock(BlockState st) {
        return st.getBlock() instanceof BuildingBlock || st.getBlock() instanceof StairBlock || st.getBlock() instanceof SlabBlock;
    }

    private static boolean isFill(BlockState s) {
        return s.getBlock() instanceof BuildingBlock && s.getValue(BuildingBlock.KIND) == BuildingBlock.Kind.FILL;
    }

    public static boolean canModify(ServerLevel level, Player p, Piece piece) {
        return p.isCreative() || p.hasPermissions(2) || p.getUUID().equals(piece.owner())
                || Util.NIL_UUID.equals(piece.owner())
                || Privilege.isAuthorized(level, piece.center(), p);
    }

    // ------------------------------------------------------------------ постановка
    public static void place(ServerPlayer sp, ServerLevel level, Placement pl) {
        Piece piece = pl.piece().withOwner(sp.getUUID(), level.getGameTime());
        if (!Privilege.canBuild(level, piece.center(), sp)) {
            msg(sp, Component.translatable("msg.rustbuild.no_privilege"));
            return;
        }
        BuildingData data = BuildingData.get(level);
        if (!Stability.placementStable(level, data, piece)) {
            msg(sp, Component.translatable("msg.rustbuild.invalid.unstable"));
            return;
        }
        int cost = cost(piece.type(), piece.tier());
        if (!sp.isCreative()) {
            if (PlayerItems.count(sp, piece.tier()) < cost) {
                msg(sp, Component.translatable("msg.rustbuild.need", cost, piece.tier().costName()));
                return;
            }
            PlayerItems.take(sp, piece.tier(), cost);
        }
        // Фундамент запоминает, что было на месте его опоры, чтобы вернуть при разрушении
        if (piece.type().isRoot()) piece = piece.withFillOrig(captureFill(level, piece));
        apply(level, piece, false);
        data.put(piece);
        level.playSound(null, piece.center(), SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0f, 0.9f);
    }

    private static BlockState[] captureFill(ServerLevel level, Piece piece) {
        List<BlockState> origs = new ArrayList<>();
        for (BlockEntry e : piece.entries()) {
            if (!isFill(e.state())) continue;
            BlockState cur = level.getBlockState(e.pos());
            origs.add(cur.getBlock() instanceof BuildingBlock ? null : cur);
        }
        return origs.toArray(new BlockState[0]);
    }

    private static void apply(ServerLevel level, Piece piece, boolean replaceBuilding) {
        for (BlockEntry e : piece.entries()) {
            BlockState cur = level.getBlockState(e.pos());
            if (!replaceBuilding && isPieceBlock(cur)) continue;
            level.setBlock(e.pos(), e.state(), 3);
        }
    }

    // ------------------------------------------------------------------ молоток: улучшить / починить / осмотреть
    public static void hammerUse(ServerPlayer sp, ServerLevel level, double reach, Tier target) {
        HitResult hr = sp.pick(reach, 1.0F, false);
        if (hr.getType() != HitResult.Type.BLOCK) { msg(sp, Component.translatable("msg.rustbuild.no_piece")); return; }
        BlockPos pos = ((BlockHitResult) hr).getBlockPos();
        BuildingData data = BuildingData.get(level);
        Piece piece = data.findAt(pos, level.getBlockState(pos));
        if (piece == null) { msg(sp, Component.translatable("msg.rustbuild.no_piece")); return; }
        if (!Privilege.canBuild(level, piece.center(), sp)) { msg(sp, Component.translatable("msg.rustbuild.no_privilege")); return; }

        if (target.ordinal() > piece.tier().ordinal()) upgrade(sp, level, data, piece, target);
        else if (piece.hp() < piece.maxHp() - 0.5f) repair(sp, level, data, piece);
        else info(sp, level, data, piece);
    }

    private static void upgrade(ServerPlayer sp, ServerLevel level, BuildingData data, Piece piece, Tier target) {
        int cost = cost(piece.type(), target);
        if (!sp.isCreative()) {
            if (PlayerItems.count(sp, target) < cost) {
                msg(sp, Component.translatable("msg.rustbuild.need", cost, target.costName()));
                return;
            }
            PlayerItems.take(sp, target, cost);
        }
        Piece upgraded = piece.withTier(target);
        apply(level, upgraded, true);
        data.put(upgraded);
        level.playSound(null, piece.center(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6f, 1.2f);
        msg(sp, Component.translatable("msg.rustbuild.upgraded", target.displayName()));
    }

    private static void repair(ServerPlayer sp, ServerLevel level, BuildingData data, Piece piece) {
        long since = level.getGameTime() - piece.lastDamage();
        if (!sp.isCreative() && since < REPAIR_COOLDOWN) {
            msg(sp, Component.translatable("msg.rustbuild.repair_cooldown", (REPAIR_COOLDOWN - since) / 20 + 1));
            return;
        }
        float missing = (piece.maxHp() - piece.hp()) / piece.maxHp();
        int cost = Math.max(1, (int) Math.ceil(cost(piece.type(), piece.tier()) * missing * 0.5f));
        if (!sp.isCreative()) {
            if (PlayerItems.count(sp, piece.tier()) < cost) {
                msg(sp, Component.translatable("msg.rustbuild.need", cost, piece.tier().costName()));
                return;
            }
            PlayerItems.take(sp, piece.tier(), cost);
        }
        data.put(piece.withHp(piece.maxHp(), piece.lastDamage()));
        level.playSound(null, piece.center(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.5f, 1.5f);
        msg(sp, Component.translatable("msg.rustbuild.repaired"));
    }

    private static void info(ServerPlayer sp, ServerLevel level, BuildingData data, Piece piece) {
        int stab = Math.round(Stability.stabilityOf(level, data, piece) * 100f);
        msg(sp, Component.translatable("msg.rustbuild.info", piece.type().displayName(), piece.tier().displayName(),
                Math.round(piece.hp()), Math.round(piece.maxHp()), stab));
    }

    // ------------------------------------------------------------------ урон и разрушение
    /** Блоки элемента, которые не принадлежат соседям (общие углы и линии стен остаются). */
    public static List<BlockEntry> exclusiveEntries(BuildingData data, Piece piece) {
        Set<BlockPos> shared = sharedPositions(data, piece);
        List<BlockEntry> out = new ArrayList<>();
        for (BlockEntry e : piece.entries()) {
            if (!shared.contains(e.pos())) out.add(e);
        }
        return out;
    }

    private static Set<BlockPos> sharedPositions(BuildingData data, Piece piece) {
        Set<BlockPos> shared = new HashSet<>();
        for (Piece o : data.neighbors(piece)) {
            for (BlockEntry e : o.entries()) shared.add(e.pos());
        }
        return shared;
    }

    /** Убирает все блоки элемента сразу, возвращает грунт под фундаментом. Без проверки обрушения. */
    public static void destroyRaw(ServerLevel level, BuildingData data, Piece piece) {
        Set<BlockPos> shared = sharedPositions(data, piece);
        BlockState[] orig = piece.fillOrig();
        int fi = 0;
        for (BlockEntry e : piece.entries()) {
            boolean fill = isFill(e.state());
            BlockState original = null;
            if (fill) {
                if (orig != null && fi < orig.length) original = orig[fi];
                fi++;
            }
            if (shared.contains(e.pos())) continue;
            BlockState cur = level.getBlockState(e.pos());
            if (!isPieceBlock(cur)) continue;
            if (fill) {
                level.levelEvent(2001, e.pos(), Block.getId(cur));
                level.setBlock(e.pos(), original != null ? original : Blocks.AIR.defaultBlockState(), 3);
            } else {
                level.destroyBlock(e.pos(), false);
            }
        }
        data.remove(piece);
    }

    /** Разрушает элемент целиком и обрушивает всё, что на нём держалось. */
    public static void destroyPiece(ServerLevel level, BuildingData data, Piece piece) {
        destroyRaw(level, data, piece);
        Stability.collapseAround(level, data, List.of(piece));
    }

    /** Наносит урон; true - элемент разрушен. markTime=false для гниения (не блокирует починку). */
    public static boolean damage(ServerLevel level, BuildingData data, Piece stale, float amount, boolean markTime) {
        Piece piece = data.get(stale.key());   // актуальная версия; null - элемент уже разрушен
        if (piece == null) return true;
        float hp = piece.hp() - amount;
        if (hp <= 0f) {
            destroyPiece(level, data, piece);
            return true;
        }
        data.put(piece.withHp(hp, markTime ? level.getGameTime() : piece.lastDamage()));
        return false;
    }

    /** Удар инструментом в ближнем бою (ЛКМ по чужой постройке). */
    public static void melee(ServerPlayer sp, ServerLevel level, BuildingData data, Piece piece) {
        float scale = sp.getAttackStrengthScale(0.5f);
        if (scale < 0.4f) return;
        sp.resetAttackStrengthTicker();
        double dmg = sp.getAttributeValue(Attributes.ATTACK_DAMAGE) * (0.2 + 0.8 * scale) * piece.tier().meleeFactor;
        BlockPos at = piece.center();
        level.playSound(null, at, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.8f, 0.8f);
        if (!damage(level, data, piece, (float) dmg, true)) {
            float left = piece.hp() - (float) dmg;
            msg(sp, Component.translatable("msg.rustbuild.hp", Math.max(0, Math.round(left)), Math.round(piece.maxHp())));
        }
    }

    /** Гниение построек без активного шкафа привилегий. Вызывается раз в 200 тиков. */
    public static void decay(ServerLevel level) {
        BuildingData data = BuildingData.get(level);
        for (Piece p : data.all()) {
            if (!level.isLoaded(p.center())) continue;
            if (Privilege.isProtected(level, p.center())) continue;
            float amount = p.maxHp() * (200f / (float) p.tier().decayTicks);
            damage(level, data, p, amount, false);
        }
    }

    // ------------------------------------------------------------------ снос молотком
    public static void demolish(ServerPlayer sp, ServerLevel level, BlockPos pos) {
        BuildingData data = BuildingData.get(level);
        Piece piece = data.findAt(pos, level.getBlockState(pos));
        if (piece == null) { msg(sp, Component.translatable("msg.rustbuild.no_piece")); return; }
        if (!canModify(level, sp, piece)) { msg(sp, Component.translatable("msg.rustbuild.not_owner")); return; }
        boolean admin = sp.isCreative() || sp.hasPermissions(2);
        if (!admin && level.getGameTime() - piece.created() > DEMOLISH_WINDOW) {
            msg(sp, Component.translatable("msg.rustbuild.demolish_expired"));
            return;
        }
        destroyPiece(level, data, piece);
        level.playSound(null, pos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.0f, 0.8f);
        msg(sp, Component.translatable("msg.rustbuild.demolished"));
    }
}
