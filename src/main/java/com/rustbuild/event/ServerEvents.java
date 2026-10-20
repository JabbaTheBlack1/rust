package com.rustbuild.event;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.rustbuild.block.BuildingBlock;
import com.rustbuild.build.BlockEntry;
import com.rustbuild.build.BuildActions;
import com.rustbuild.build.BuildingData;
import com.rustbuild.build.Piece;
import com.rustbuild.build.PieceType;
import com.rustbuild.build.Tier;
import com.rustbuild.item.BuildingPlanItem;
import com.rustbuild.item.HammerItem;
import com.rustbuild.item.ItemData;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ServerEvents {
    private ServerEvents() {}

    private static boolean isBuildingState(BlockState st) {
        return st.getBlock() instanceof BuildingBlock || st.getBlock() instanceof StairBlock;
    }

    /** Сломать любой блок элемента = сломать весь элемент (только владелец / оператор / креатив). */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        BlockState st = e.getState();
        if (!isBuildingState(st)) return;

        BuildingData data = BuildingData.get(level);
        Piece piece = data.findAt(e.getPos(), st);
        if (piece == null) return; // записи нет (старая постройка) - ломается как обычный блок

        e.setCanceled(true);
        Player p = e.getPlayer();
        if (!BuildActions.canModify(p, piece)) {
            p.displayClientMessage(Component.translatable("msg.rustbuild.not_owner"), true);
            return;
        }
        BuildActions.destroyPiece(level, data, piece);
    }

    /** Взрыв затронул блок элемента -> разрушается весь элемент. */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        List<BlockPos> affected = e.getAffectedBlocks();
        if (affected.isEmpty()) return;

        BuildingData data = BuildingData.get(level);
        Set<String> seen = new HashSet<>();
        List<Piece> hit = new ArrayList<>();
        for (BlockPos pos : affected) {
            BlockState st = level.getBlockState(pos);
            if (!isBuildingState(st)) continue;
            Piece p = data.findAt(pos, st);
            if (p != null && seen.add(p.key())) hit.add(p);
        }
        if (hit.isEmpty()) return;

        Set<BlockPos> already = new HashSet<>(affected);
        for (Piece p : hit) {
            for (BlockEntry en : BuildActions.exclusiveEntries(data, p)) {
                if (already.add(en.pos())) affected.add(en.pos());
            }
            data.remove(p);
        }
    }

    /** ЛКМ молотком = снос элемента. */
    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock e) {
        if (!(e.getItemStack().getItem() instanceof HammerItem)) return;
        e.setCanceled(true);
        if (e.getLevel().isClientSide()) return;
        if (e.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        if (e.getEntity() instanceof ServerPlayer sp && e.getLevel() instanceof ServerLevel level) {
            BuildActions.demolish(sp, level, e.getPos());
        }
    }

    // ---- команды, которыми радиальное меню сообщает серверу выбор ----
    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("rustbuild")
                .then(Commands.literal("piece")
                        .then(Commands.argument("index", IntegerArgumentType.integer(0, PieceType.values().length - 1))
                                .executes(c -> {
                                    selectPiece(c.getSource().getPlayerOrException(),
                                            IntegerArgumentType.getInteger(c, "index"));
                                    return 1;
                                })))
                .then(Commands.literal("tier")
                        .then(Commands.argument("index", IntegerArgumentType.integer(0, Tier.values().length - 1))
                                .executes(c -> {
                                    selectTier(c.getSource().getPlayerOrException(),
                                            IntegerArgumentType.getInteger(c, "index"));
                                    return 1;
                                }))));
    }

    private static void selectPiece(ServerPlayer p, int index) {
        ItemStack stack = p.getMainHandItem();
        if (!(stack.getItem() instanceof BuildingPlanItem)) return;
        PieceType type = PieceType.byIndex(index);
        ItemData.setInt(stack, "piece", type.ordinal());
        p.displayClientMessage(Component.translatable("msg.rustbuild.piece_selected", type.displayName()), true);
    }

    private static void selectTier(ServerPlayer p, int index) {
        ItemStack stack = p.getMainHandItem();
        if (!(stack.getItem() instanceof HammerItem)) return;
        Tier tier = Tier.values()[index];
        ItemData.setInt(stack, "tier", tier.ordinal());
        p.displayClientMessage(Component.translatable("msg.rustbuild.tier_selected", tier.displayName()), true);
    }
}
