package com.rustbuild.build;

import com.rustbuild.block.ToolCupboardBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/** Право строить рядом с шкафом привилегий. */
public final class Privilege {
    public static final int RADIUS = 32;

    private Privilege() {}

    /** Игрок авторизован в шкафах, накрывающих точку (или шкафов нет вообще). */
    public static boolean isAuthorized(ServerLevel level, BlockPos pos, Player p) {
        List<BlockPos> tcs = BuildingData.get(level).cupboardsNear(pos, RADIUS);
        boolean any = false;
        for (BlockPos tc : tcs) {
            BlockEntity be = level.getBlockEntity(tc);
            if (be instanceof ToolCupboardBlockEntity t) {
                any = true;
                if (t.isAuthorized(p.getUUID())) return true;
            }
        }
        return !any;
    }

    public static boolean canBuild(ServerLevel level, BlockPos pos, Player p) {
        if (p.isCreative() && p.hasPermissions(2)) return true;
        return isAuthorized(level, pos, p);
    }

    /** Защищена ли точка активным шкафом (есть ресурсы на содержание) - тогда постройки не гниют. */
    public static boolean isProtected(ServerLevel level, BlockPos pos) {
        for (BlockPos tc : BuildingData.get(level).cupboardsNear(pos, RADIUS)) {
            if (!level.isLoaded(tc)) return true;
            BlockEntity be = level.getBlockEntity(tc);
            if (be instanceof ToolCupboardBlockEntity t && t.isActive()) return true;
        }
        return false;
    }
}
