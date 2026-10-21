package com.rustbuild.build;

import com.rustbuild.entity.ModEntities;
import com.rustbuild.entity.PieceEntity;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/** Серверные действия: постановка, улучшение, починка, урон, снос. Элемент обрабатывается ЦЕЛИКОМ. */
public final class BuildActions {
    /** 10 минут после постройки, как в Rust, можно сносить молотком. */
    public static final long DEMOLISH_WINDOW = 12000L;
    /** 30 секунд после получения урона чинить нельзя. */
    public static final long REPAIR_COOLDOWN = 600L;
    public static final double REACH = 10.0;

    private BuildActions() {}

    public static int cost(PieceType type, Tier tier) { return type.costMultiplier * tier.unitCost; }

    private static void msg(ServerPlayer p, Component c) { p.displayClientMessage(c, true); }

    public static boolean canModify(ServerLevel level, Player p, Piece piece) {
        return p.isCreative() || p.hasPermissions(2) || p.getUUID().equals(piece.owner())
                || Util.NIL_UUID.equals(piece.owner())
                || Privilege.isAuthorized(level, piece.blockPos(), p);
    }

    /** Элемент, на который смотрит игрок (точный луч по телам). */
    @Nullable
    public static PieceRaycast.Hit pick(ServerPlayer sp, ServerLevel level, double reach) {
        Vec3 eye = sp.getEyePosition(1.0F);
        Vec3 look = sp.getViewVector(1.0F);
        BuildingData data = BuildingData.get(level);
        return PieceRaycast.pick(data.inBox(new AABB(eye, eye.add(look.scale(reach))).inflate(4.0)), eye, look, reach);
    }

    // ------------------------------------------------------------------ постановка
    public static void place(ServerPlayer sp, ServerLevel level, Placement pl) {
        Piece piece = pl.piece().withId(UUID.randomUUID()).withOwner(sp.getUUID(), level.getGameTime());
        if (!Privilege.canBuild(level, piece.blockPos(), sp)) {
            msg(sp, Component.translatable("msg.rustbuild.no_privilege"));
            return;
        }
        int cost = cost(piece.type(), piece.tier());
        if (!sp.isCreative() && PlayerItems.count(sp, piece.tier()) < cost) {
            msg(sp, Component.translatable("msg.rustbuild.need", cost, piece.tier().costName()));
            return;
        }

        BuildingData data = BuildingData.get(level);
        data.put(piece);
        Links.connect(data, piece);
        Piece linked = data.getPiece(piece.id());
        if (linked != null && !piece.type().isRoot() && Stability.stabilityOf(data, linked) < Stability.PLACE_MIN) {
            Links.disconnect(data, linked);
            data.remove(linked);
            msg(sp, Component.translatable("msg.rustbuild.invalid.unstable"));
            return;
        }
        if (!sp.isCreative()) PlayerItems.take(sp, piece.tier(), cost);

        PieceEntity e = new PieceEntity(ModEntities.PIECE.get(), level);
        e.setUUID(piece.id());
        e.setPos(piece.x(), piece.y(), piece.z());
        e.setup(piece);
        level.addFreshEntity(e);
        Collision.apply(level, piece);
        level.playSound(null, piece.blockPos(), SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0f, 0.9f);
    }

    // ------------------------------------------------------------------ молоток: улучшить / починить / осмотреть
    public static void hammerUse(ServerPlayer sp, ServerLevel level, double reach, Tier target) {
        PieceRaycast.Hit hit = pick(sp, level, reach);
        if (hit == null) { msg(sp, Component.translatable("msg.rustbuild.no_piece")); return; }
        Piece piece = hit.piece();
        if (!Privilege.canBuild(level, piece.blockPos(), sp)) { msg(sp, Component.translatable("msg.rustbuild.no_privilege")); return; }
        BuildingData data = BuildingData.get(level);

        if (target.ordinal() > piece.tier().ordinal()) upgrade(sp, level, data, piece, target);
        else if (piece.hp() < piece.maxHp() - 0.5f) repair(sp, level, data, piece);
        else info(sp, data, piece);
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
        data.put(upgraded);
        Entity e = level.getEntity(piece.id());
        if (e instanceof PieceEntity pe) pe.setup(upgraded);
        level.playSound(null, piece.blockPos(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6f, 1.2f);
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
        level.playSound(null, piece.blockPos(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.5f, 1.5f);
        msg(sp, Component.translatable("msg.rustbuild.repaired"));
    }

    private static void info(ServerPlayer sp, BuildingData data, Piece piece) {
        int stab = Math.round(Stability.stabilityOf(data, piece) * 100f);
        msg(sp, Component.translatable("msg.rustbuild.info", piece.type().displayName(), piece.tier().displayName(),
                Math.round(piece.hp()), Math.round(piece.maxHp()), stab));
    }

    // ------------------------------------------------------------------ урон и разрушение
    /** Убирает элемент: сущность, коллизию, связи, запись. Без проверки обрушения. */
    public static void destroyRaw(ServerLevel level, BuildingData data, Piece piece) {
        Collision.remove(level, piece);
        Entity e = level.getEntity(piece.id());
        if (e != null) e.discard();
        Links.disconnect(data, piece);
        data.remove(piece);
        Vec3 c = piece.toWorld(0, 0.5, 0);
        level.levelEvent(2001, net.minecraft.core.BlockPos.containing(c), net.minecraft.world.level.block.Block.getId(
                net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState()));
    }

    /** Разрушает элемент целиком и обрушивает всё, что на нём держалось. */
    public static void destroyPiece(ServerLevel level, BuildingData data, Piece piece) {
        List<UUID> seeds = new java.util.ArrayList<>();
        seeds.addAll(piece.supporting()); seeds.addAll(piece.adj()); seeds.addAll(piece.base()); seeds.addAll(piece.below());
        destroyRaw(level, data, piece);
        Stability.collapseAround(level, data, seeds);
    }

    /** Наносит урон; true - элемент разрушен. markTime=false для гниения (не блокирует починку). */
    public static boolean damage(ServerLevel level, BuildingData data, Piece stale, float amount, boolean markTime) {
        Piece piece = data.getPiece(stale.id());
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
        level.playSound(null, piece.blockPos(), SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.8f, 0.8f);
        if (!damage(level, data, piece, (float) dmg, true)) {
            float left = piece.hp() - (float) dmg;
            msg(sp, Component.translatable("msg.rustbuild.hp", Math.max(0, Math.round(left)), Math.round(piece.maxHp())));
        }
    }

    /** Расстояние от точки (взрыв) до ближайшей части элемента. */
    public static double distanceTo(Piece piece, Vec3 point) {
        Vec3 l = piece.toLocal(point);
        double best = Double.MAX_VALUE;
        for (AABB b : PieceDef.previewBoxes(piece.type(), piece.depth())) {
            best = Math.min(best, Math.sqrt(b.distanceToSqr(l)));
        }
        return best;
    }

    /** Гниение построек без активного шкафа привилегий. Вызывается раз в 200 тиков. */
    public static void decay(ServerLevel level) {
        BuildingData data = BuildingData.get(level);
        for (Piece p : data.all()) {
            if (!level.isLoaded(p.blockPos())) continue;
            if (Privilege.isProtected(level, p.blockPos())) continue;
            float amount = p.maxHp() * (200f / (float) p.tier().decayTicks);
            damage(level, data, p, amount, false);
        }
    }

    // ------------------------------------------------------------------ снос молотком
    public static void demolish(ServerPlayer sp, ServerLevel level) {
        PieceRaycast.Hit hit = pick(sp, level, REACH);
        if (hit == null) { msg(sp, Component.translatable("msg.rustbuild.no_piece")); return; }
        Piece piece = hit.piece();
        BuildingData data = BuildingData.get(level);
        if (!canModify(level, sp, piece)) { msg(sp, Component.translatable("msg.rustbuild.not_owner")); return; }
        boolean admin = sp.isCreative() || sp.hasPermissions(2);
        if (!admin && level.getGameTime() - piece.created() > DEMOLISH_WINDOW) {
            msg(sp, Component.translatable("msg.rustbuild.demolish_expired"));
            return;
        }
        destroyPiece(level, data, piece);
        level.playSound(null, piece.blockPos(), SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.0f, 0.8f);
        msg(sp, Component.translatable("msg.rustbuild.demolished"));
    }
}
