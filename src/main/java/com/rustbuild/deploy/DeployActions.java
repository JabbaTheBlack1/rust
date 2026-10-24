package com.rustbuild.deploy;

import com.rustbuild.build.BuildingData;
import com.rustbuild.build.Collision;
import com.rustbuild.build.Piece;
import com.rustbuild.build.PieceRaycast;
import com.rustbuild.build.Privilege;
import com.rustbuild.build.Solid;
import com.rustbuild.build.Xf;
import com.rustbuild.entity.ModEntities;
import com.rustbuild.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/** Постановка, подбор и поиск свободных предметов. */
public final class DeployActions {
    private DeployActions() {}

    public static ItemStack itemFor(DeployKind kind, ItemStack decor) {
        return switch (kind) {
            case TOOL_CUPBOARD -> new ItemStack(ModItems.TOOL_CUPBOARD.get());
            case STORAGE_BOX -> new ItemStack(ModItems.STORAGE_BOX.get());
            case WORKBENCH -> new ItemStack(ModItems.WORKBENCH.get());
            case DECOR -> decor.copyWithCount(1);
        };
    }

    public static boolean place(ServerPlayer sp, ServerLevel level, DeployKind kind, ItemStack stack, DeployResolver.Placed pl) {
        Xf xf = pl.xf();
        BlockPos at = BlockPos.containing(xf.pos());
        if (!Privilege.canBuild(level, at, sp)) {
            sp.displayClientMessage(Component.translatable("msg.rustbuild.no_privilege"), true);
            return false;
        }
        DeployableEntity e = new DeployableEntity(ModEntities.DEPLOYABLE.get(), level);
        e.setPos(xf.x(), xf.y(), xf.z());
        e.setup(kind, stack, xf.yaw(), sp.getUUID());
        level.addFreshEntity(e);

        if (DeployDef.collides(kind, stack)) {
            Collision.apply(level, e.getUUID(), Collision.voxelize(e.worldBounds(), xf::toLocal, e.solids()));
        }
        if (kind == DeployKind.TOOL_CUPBOARD) {
            e.authorize(sp);
            BuildingData.get(level).addCupboardEntity(e.getUUID(), at);
        }
        level.playSound(null, at, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0f, 0.9f);
        return true;
    }

    /** Забрать предмет обратно (владелец или авторизованный). */
    public static void pickup(ServerPlayer sp, ServerLevel level, DeployableEntity e) {
        boolean ok = sp.isCreative() || sp.hasPermissions(2) || sp.getUUID().equals(e.owner())
                || Privilege.isAuthorized(level, e.blockPosition(), sp);
        if (!ok) {
            sp.displayClientMessage(Component.translatable("msg.rustbuild.not_owner"), true);
            return;
        }
        remove(level, e);
        ItemStack back = itemFor(e.kind(), e.stack());
        if (!sp.getInventory().add(back)) sp.drop(back, false);
    }

    /** Убрать предмет: коллизия, реестр шкафов, содержимое. */
    public static void remove(ServerLevel level, DeployableEntity e) {
        if (DeployDef.collides(e.kind(), e.stack())) {
            Map<BlockPos, Long> vox = Collision.voxelize(e.worldBounds(), e.xf()::toLocal, e.solids());
            Collision.remove(level, e.getUUID(), vox.keySet());
        }
        if (e.kind() == DeployKind.TOOL_CUPBOARD) BuildingData.get(level).removeCupboardEntity(e.getUUID());
        e.dropContents();
        e.discard();
    }

    /** Свободный предмет, на который смотрит игрок (работает на клиенте и сервере). */
    @Nullable
    public static DeployableEntity pick(Player player, double reach) {
        Level level = player.level();
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        List<DeployableEntity> near = level.getEntitiesOfClass(DeployableEntity.class,
                new AABB(eye, eye.add(look.scale(reach))).inflate(2.0));
        DeployableEntity best = null;
        double bestT = Double.MAX_VALUE;
        for (DeployableEntity d : near) {
            Xf xf = d.xf();
            Vec3 o = xf.toLocal(eye);
            Vec3 dir = xf.dirToLocal(look);
            for (Solid s : d.solids()) {
                double t = s.clip(o.x, o.y, o.z, dir.x, dir.y, dir.z);
                if (!Double.isNaN(t) && t <= reach && t < bestT) { bestT = t; best = d; }
            }
        }
        return best;
    }
}
