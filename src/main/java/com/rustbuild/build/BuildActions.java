package com.rustbuild.build;

import com.rustbuild.block.BuildingBlock;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Серверные действия: постановка, улучшение, снос. Элемент всегда обрабатывается ЦЕЛИКОМ. */
public final class BuildActions {
    private BuildActions() {}

    public static int cost(PieceType type, Tier tier) {
        return type.costMultiplier * tier.unitCost;
    }

    private static void msg(ServerPlayer p, Component c) { p.displayClientMessage(c, true); }

    public static boolean canModify(Player p, Piece piece) {
        return p.isCreative() || p.hasPermissions(2)
                || p.getUUID().equals(piece.owner()) || Util.NIL_UUID.equals(piece.owner());
    }

    public static void place(ServerPlayer sp, ServerLevel level, Placement pl) {
        Piece piece = pl.piece().withOwner(sp.getUUID(), level.getGameTime());
        int cost = cost(piece.type(), piece.tier());
        if (!sp.isCreative()) {
            if (PlayerItems.count(sp, piece.tier()) < cost) {
                msg(sp, Component.translatable("msg.rustbuild.need", cost, piece.tier().costName()));
                return;
            }
            PlayerItems.take(sp, piece.tier(), cost);
        }
        apply(level, piece, false);
        BuildingData.get(level).put(piece);
        BlockPos at = piece.entries().get(0).pos();
        level.playSound(null, at, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0f, 0.9f);
    }

    private static void apply(ServerLevel level, Piece piece, boolean replaceBuilding) {
        for (BlockEntry e : piece.entries()) {
            BlockState cur = level.getBlockState(e.pos());
            if (!replaceBuilding && cur.getBlock() instanceof BuildingBlock) continue;
            level.setBlock(e.pos(), e.state(), 3);
        }
    }

    public static void upgrade(ServerPlayer sp, ServerLevel level, double reach, Tier target) {
        HitResult hr = sp.pick(reach, 1.0F, false);
        if (hr.getType() != HitResult.Type.BLOCK) { msg(sp, Component.translatable("msg.rustbuild.no_piece")); return; }
        BlockPos pos = ((BlockHitResult) hr).getBlockPos();
        BuildingData data = BuildingData.get(level);
        Piece piece = data.findAt(pos, level.getBlockState(pos));
        if (piece == null) { msg(sp, Component.translatable("msg.rustbuild.no_piece")); return; }
        if (target.ordinal() <= piece.tier().ordinal()) { msg(sp, Component.translatable("msg.rustbuild.already")); return; }

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
        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6f, 1.2f);
        msg(sp, Component.translatable("msg.rustbuild.upgraded", target.displayName()));
    }

    /** Блоки элемента, которые не принадлежат соседям (общие углы и линии стен остаются). */
    public static List<BlockEntry> exclusiveEntries(BuildingData data, Piece piece) {
        Set<BlockPos> shared = new HashSet<>();
        for (Piece o : data.neighbors(piece)) {
            for (BlockEntry e : o.entries()) shared.add(e.pos());
        }
        List<BlockEntry> out = new ArrayList<>();
        for (BlockEntry e : piece.entries()) {
            if (!shared.contains(e.pos())) out.add(e);
        }
        return out;
    }

    /** Разрушает элемент целиком: все его блоки исчезают разом, запись удаляется. */
    public static void destroyPiece(ServerLevel level, BuildingData data, Piece piece) {
        for (BlockEntry e : exclusiveEntries(data, piece)) {
            BlockState cur = level.getBlockState(e.pos());
            boolean isBuild = cur.getBlock() instanceof BuildingBlock;
            if (!isBuild && !(cur.getBlock() instanceof StairBlock)) continue;
            boolean fill = isBuild && cur.getValue(BuildingBlock.KIND) == BuildingBlock.Kind.FILL;
            if (fill) {
                level.levelEvent(2001, e.pos(), Block.getId(cur));
                level.setBlock(e.pos(), Blocks.DIRT.defaultBlockState(), 3);
            } else {
                level.destroyBlock(e.pos(), false);
            }
        }
        data.remove(piece);
    }

    /** Снос молотком (ЛКМ). */
    public static void demolish(ServerPlayer sp, ServerLevel level, BlockPos pos) {
        BuildingData data = BuildingData.get(level);
        Piece piece = data.findAt(pos, level.getBlockState(pos));
        if (piece == null) { msg(sp, Component.translatable("msg.rustbuild.no_piece")); return; }
        if (!canModify(sp, piece)) { msg(sp, Component.translatable("msg.rustbuild.not_owner")); return; }
        destroyPiece(level, data, piece);
        level.playSound(null, pos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.0f, 0.8f);
        msg(sp, Component.translatable("msg.rustbuild.demolished"));
    }
}
