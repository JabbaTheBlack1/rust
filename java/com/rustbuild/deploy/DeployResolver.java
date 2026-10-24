package com.rustbuild.deploy;

import com.rustbuild.block.CollisionBlock;
import com.rustbuild.build.Piece;
import com.rustbuild.build.PieceRaycast;
import com.rustbuild.build.Resolver;
import com.rustbuild.build.Solid;
import com.rustbuild.build.Xf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Свободная постановка предметов: в точку прицела на любой горизонтальной поверхности (земля, пол, фундамент), под любым углом. */
public final class DeployResolver {
    private static final double REACH = 8.0;

    public record Placed(Xf xf, List<Solid> solids, boolean valid, String reason) {}

    private DeployResolver() {}

    @Nullable
    public static Placed resolve(Level level, Player player, DeployKind kind, ItemStack stack, int rotDeg,
                                 Function<AABB, List<Piece>> pieces) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(REACH));

        PieceRaycast.Hit ph = PieceRaycast.pick(pieces.apply(new AABB(eye, end).inflate(4.0)), eye, look, REACH);
        BlockHitResult th = Resolver.terrainRay(level, eye, end);
        if (ph == null && th == null) return null;

        double dt = th == null ? Double.MAX_VALUE : th.getLocation().distanceTo(eye);
        double dp = ph == null ? Double.MAX_VALUE : ph.dist();
        Vec3 aim;
        boolean up;
        if (dp < dt) {
            aim = ph.point();
            up = ph.normal().y > 0.7;
        } else {
            aim = th.getLocation();
            up = th.getDirection() == Direction.UP;
        }
        if (!up) return null;

        Xf xf = new Xf(aim.x, aim.y, aim.z, Math.round(-player.getYRot() + rotDeg));
        List<Solid> solids = DeployDef.solids(kind, stack);
        String reason = check(level, xf, solids, pieces.apply(new AABB(aim, aim).inflate(6.0)));
        return new Placed(xf, solids, reason == null, reason);
    }

    @Nullable
    private static String check(Level level, Xf xf, List<Solid> solids, List<Piece> near) {
        List<Vec3> samples = new ArrayList<>();
        for (Solid s : solids) {
            AABB b = s.bounds();
            for (double lx : axis(b.minX, b.maxX)) for (double ly : axis(b.minY, b.maxY)) for (double lz : axis(b.minZ, b.maxZ)) {
                if (s.contains(lx, ly, lz, 0.04)) samples.add(xf.toWorld(lx, ly, lz));
            }
        }
        for (Piece q : near) {
            if (q.pos().distanceTo(xf.pos()) > 7.0) continue;
            List<Solid> qs = q.solids();
            for (Vec3 w : samples) {
                Vec3 l = q.toLocal(w);
                for (Solid s : qs) if (s.contains(l.x, l.y, l.z, 0.0)) return "blocked";
            }
        }
        AABB box = xf.bounds(solids).inflate(0.5);
        for (DeployableEntity d : level.getEntitiesOfClass(DeployableEntity.class, box)) {
            Xf dx = d.xf();
            List<Solid> ds = d.solids();
            for (Vec3 w : samples) {
                Vec3 l = dx.toLocal(w);
                for (Solid s : ds) if (s.contains(l.x, l.y, l.z, 0.0)) return "blocked";
            }
        }
        for (Vec3 w : samples) {
            BlockPos bp = BlockPos.containing(w);
            BlockState st = level.getBlockState(bp);
            if (!st.getFluidState().isEmpty()) return "water";
            if (st.getBlock() instanceof CollisionBlock) continue;
            VoxelShape sh = st.getCollisionShape(level, bp);
            if (!sh.isEmpty() && sh.bounds().move(bp).contains(w)) return "blocked";
        }
        return null;
    }

    private static double[] axis(double min, double max) {
        double a = min + 0.05, b = max - 0.05;
        if (b <= a) return new double[]{(min + max) / 2};
        int n = Math.max(1, (int) Math.ceil((b - a) / 0.3));
        double[] r = new double[n + 1];
        for (int i = 0; i <= n; i++) r[i] = a + (b - a) * i / n;
        return r;
    }
}
