package com.rustbuild.event;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.rustbuild.block.CollisionBlock;
import com.rustbuild.block.ToolCupboardBlock;
import com.rustbuild.build.BuildActions;
import com.rustbuild.build.BuildingData;
import com.rustbuild.build.Piece;
import com.rustbuild.build.PieceRaycast;
import com.rustbuild.build.PieceType;
import com.rustbuild.build.Privilege;
import com.rustbuild.build.Tier;
import com.rustbuild.deploy.DeployActions;
import com.rustbuild.deploy.DeployKind;
import com.rustbuild.deploy.DeployResolver;
import com.rustbuild.deploy.DeployableEntity;
import com.rustbuild.item.BuildingPlanItem;
import com.rustbuild.item.DeployableItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import com.rustbuild.item.HammerItem;
import com.rustbuild.item.ItemData;
import net.minecraft.commands.Commands;
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

public final class ServerEvents {
    private ServerEvents() {}

    // ------------------------------------------------------------------ ломание
    /** Невидимые блоки-коллайдеры не ломаются; в креативе удар разрушает весь элемент, на который смотрит игрок. */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        BlockState st = e.getState();
        Player p = e.getPlayer();

        if (st.getBlock() instanceof ToolCupboardBlock) {
            if (!p.isCreative() && !Privilege.isAuthorized(level, e.getPos(), p)) {
                e.setCanceled(true);
                p.displayClientMessage(Component.translatable("msg.rustbuild.cupboard_locked"), true);
            }
            return;
        }
        if (!(st.getBlock() instanceof CollisionBlock)) return;

        e.setCanceled(true);
        if (p.isCreative() && p instanceof ServerPlayer sp) {
            PieceRaycast.Hit hit = BuildActions.pick(sp, level, BuildActions.REACH);
            if (hit != null) BuildActions.destroyPiece(level, BuildingData.get(level), hit.piece());
        }
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
            if (pl instanceof ServerPlayer sp && lv instanceof ServerLevel level) BuildActions.demolish(sp, level);
            return;
        }

        if (!(lv.getBlockState(e.getPos()).getBlock() instanceof CollisionBlock) || pl.isCreative()) return;
        e.setCanceled(true);   // и на клиенте: не показывать "ломание"

        if (lv.isClientSide() || e.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        if (!(pl instanceof ServerPlayer sp) || !(lv instanceof ServerLevel level)) return;
        PieceRaycast.Hit hit = BuildActions.pick(sp, level, BuildActions.REACH);
        if (hit == null) return;
        Piece piece = hit.piece();
        if (BuildActions.canModify(level, sp, piece)) {
            sp.displayClientMessage(Component.translatable("msg.rustbuild.use_hammer"), true);
        } else {
            BuildActions.melee(sp, level, BuildingData.get(level), piece);
        }
    }

    // ------------------------------------------------------------------ правый клик по свободному предмету сквозь коллизию
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (e.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(e.getLevel().getBlockState(e.getPos()).getBlock() instanceof CollisionBlock)) return;
        var held = e.getItemStack().getItem();
        if (held instanceof BuildingPlanItem || held instanceof HammerItem || held instanceof DeployableItem) return;
        Player p = e.getEntity();
        DeployableEntity d = DeployActions.pick(p, 6.0);
        if (d == null) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (!e.getLevel().isClientSide() && p instanceof ServerPlayer sp) d.interact(sp, InteractionHand.MAIN_HAND);
    }

    // ------------------------------------------------------------------ взрывы наносят урон по радиусу
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        e.getAffectedBlocks().removeIf(pos -> level.getBlockState(pos).getBlock() instanceof CollisionBlock);

        Entity src = e.getExplosion().getDirectSourceEntity();
        if (src == null) return;
        Vec3 center = src.position();
        float radius = 4.0f, power = 350f;
        if (src instanceof Creeper c) {
            radius = c.isPowered() ? 6.0f : 3.5f;
            power = c.isPowered() ? 600f : 250f;
        }

        BuildingData data = BuildingData.get(level);
        for (Piece p : data.inBox(new AABB(center, center).inflate(radius + 4.0))) {
            double d = BuildActions.distanceTo(p, center);
            if (d >= radius) continue;
            float dmg = (float) (power * (1.0 - d / radius));
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

    // ------------------------------------------------------------------ команды меню и поворота
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
                                })))
                .then(Commands.literal("place")
                        .executes(c -> {
                            placeHeld(c.getSource().getPlayerOrException());
                            return 1;
                        }))
                .then(Commands.literal("rot")
                        .then(Commands.argument("deg", IntegerArgumentType.integer(-360, 360))
                                .executes(c -> {
                                    rotate(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "deg"));
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

    /** V: поставить предмет из руки свободно в точку прицела под любым углом. */
    private static void placeHeld(ServerPlayer p) {
        ItemStack held = p.getMainHandItem();
        if (held.isEmpty() || held.getItem() instanceof BuildingPlanItem || held.getItem() instanceof HammerItem) return;
        if (!(p.level() instanceof ServerLevel level)) return;
        DeployKind kind = held.getItem() instanceof DeployableItem di ? di.kind : DeployKind.forStack(held);
        ItemStack one = held.copyWithCount(1);
        BuildingData data = BuildingData.get(level);
        DeployResolver.Placed pl = DeployResolver.resolve(level, p, kind, one, 0, box -> data.inBox(box));
        if (pl == null) { p.displayClientMessage(Component.translatable("msg.rustbuild.aim_floor"), true); return; }
        if (!pl.valid()) { p.displayClientMessage(Component.translatable("msg.rustbuild.invalid." + pl.reason()), true); return; }
        if (DeployActions.place(p, level, kind, one, pl) && !p.isCreative()) held.shrink(1);
    }

    private static void rotate(ServerPlayer p, int delta) {
        ItemStack stack = p.getMainHandItem();
        boolean plan = stack.getItem() instanceof BuildingPlanItem;
        if (!plan && !(stack.getItem() instanceof DeployableItem)) return;
        int cur = plan ? BuildingPlanItem.getRot(stack) : DeployableItem.getRot(stack);
        int rot = Math.floorMod(cur + delta, 360);
        ItemData.setInt(stack, "rot", rot);
        p.displayClientMessage(Component.translatable("msg.rustbuild.rotation", rot), true);
    }
}
