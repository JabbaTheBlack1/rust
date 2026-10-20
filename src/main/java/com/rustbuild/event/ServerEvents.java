package com.rustbuild.event;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.rustbuild.block.BuildingBlock;
import com.rustbuild.block.ToolCupboardBlock;
import com.rustbuild.build.BuildActions;
import com.rustbuild.build.BuildingData;
import com.rustbuild.build.Piece;
import com.rustbuild.build.PieceType;
import com.rustbuild.build.Privilege;
import com.rustbuild.build.Tier;
import com.rustbuild.item.BuildingPlanItem;
import com.rustbuild.item.HammerItem;
import com.rustbuild.item.ItemData;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.List;

public final class ServerEvents {
    private ServerEvents() {}

    // ------------------------------------------------------------------ разрушение руками (креатив)
    /** В выживании ломать элементы руками нельзя (урон наносится ударом, см. onLeftClick). В креативе - целиком. */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        BlockState st = e.getState();
        if (st.getBlock() instanceof ToolCupboardBlock) {
            Player p = e.getPlayer();
            if (!p.isCreative() && !Privilege.isAuthorized(level, e.getPos(), p)) {
                e.setCanceled(true);
                p.displayClientMessage(Component.translatable("msg.rustbuild.cupboard_locked"), true);
            }
            return;
        }
        if (!BuildActions.isPieceBlock(st)) return;

        BuildingData data = BuildingData.get(level);
        Piece piece = data.findAt(e.getPos(), st);
        if (piece == null) return; // записи нет (старая постройка) - ломается как обычный блок

        e.setCanceled(true);
        Player p = e.getPlayer();
        if (p.isCreative()) BuildActions.destroyPiece(level, data, piece);
    }

    // ------------------------------------------------------------------ удары: молоток / ближний бой
    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock e) {
        Player pl = e.getEntity();
        Level lv = e.getLevel();
        ItemStack held = e.getItemStack();

        if (held.getItem() instanceof HammerItem) {
            e.setCanceled(true);
            if (lv.isClientSide() || e.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
            if (pl instanceof ServerPlayer sp && lv instanceof ServerLevel level) {
                BuildActions.demolish(sp, level, e.getPos());
            }
            return;
        }

        BlockState st = lv.getBlockState(e.getPos());
        if (!BuildActions.isPieceBlock(st) || pl.isCreative()) return;
        if (st.getBlock() instanceof BuildingBlock) e.setCanceled(true); // и на клиенте: не показывать "ломание"

        if (lv.isClientSide() || e.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        if (!(pl instanceof ServerPlayer sp) || !(lv instanceof ServerLevel level)) return;
        BuildingData data = BuildingData.get(level);
        Piece piece = data.findAt(e.getPos(), st);
        if (piece == null) return;
        e.setCanceled(true);

        if (BuildActions.canModify(level, sp, piece)) {
            sp.displayClientMessage(Component.translatable("msg.rustbuild.use_hammer"), true);
        } else {
            BuildActions.melee(sp, level, data, piece);
        }
    }

    // ------------------------------------------------------------------ взрывы наносят урон, а не ломают поштучно
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        BuildingData data = BuildingData.get(level);
        List<BlockPos> affected = e.getAffectedBlocks();

        // блоки элементов не ломаются поштучно
        affected.removeIf(pos -> {
            BlockState st = level.getBlockState(pos);
            return BuildActions.isPieceBlock(st) && data.findAt(pos, st) != null;
        });

        Entity src = e.getExplosion().getDirectSourceEntity();
        if (src == null) return;
        Vec3 center = src.position();
        float radius = 4.0f, power = 350f;
        if (src instanceof Creeper c) {
            radius = c.isPowered() ? 6.0f : 3.5f;
            power = c.isPowered() ? 600f : 250f;
        }

        double maxD = radius + 8.0;
        for (Piece p : data.all()) {
            BlockPos pc = p.center();
            if (center.distanceToSqr(pc.getX() + 0.5, pc.getY() + 0.5, pc.getZ() + 0.5) > maxD * maxD) continue;
            double best = Double.MAX_VALUE;
            for (AABB box : p.previewBoxes()) best = Math.min(best, Math.sqrt(box.distanceToSqr(center)));
            if (best >= radius) continue;
            float dmg = (float) (power * (1.0 - best / radius));
            if (dmg > 0f) BuildActions.damage(level, data, p, dmg, true);
        }
    }

    // ------------------------------------------------------------------ шкаф: право ставить
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        if (!(e.getPlacedBlock().getBlock() instanceof ToolCupboardBlock)) return;
        if (e.getEntity() instanceof Player p && !Privilege.canBuild(level, e.getPos(), p)) {
            e.setCanceled(true);
            p.displayClientMessage(Component.translatable("msg.rustbuild.no_privilege"), true);
        }
    }

    // ------------------------------------------------------------------ гниение
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % 200 != 0) return;
        for (ServerLevel level : server.getAllLevels()) BuildActions.decay(level);
    }

    // ------------------------------------------------------------------ команды радиального меню
    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("rustbuild")
                .then(Commands.literal("piece")
                        .then(Commands.argument("index", IntegerArgumentType.integer(0, PieceType.values().length - 1))
                                .executes(c -> {
                                    selectPiece(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "index"));
                                    return 1;
                                })))
                .then(Commands.literal("tier")
                        .then(Commands.argument("index", IntegerArgumentType.integer(0, Tier.values().length - 1))
                                .executes(c -> {
                                    selectTier(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "index"));
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
