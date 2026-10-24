package com.rustbuild.build;

import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Collection;

/** Луч против тел элементов (в их локальных координатах). */
public final class PieceRaycast {
    public record Hit(Piece piece, double dist, Vec3 point, Vec3 normal) {}

    private PieceRaycast() {}

    @Nullable
    public static Hit pick(Collection<Piece> pieces, Vec3 eye, Vec3 dir, double reach) {
        Hit best = null;
        for (Piece p : pieces) {
            if (p.pos().distanceTo(eye) > reach + 8.0) continue;
            Vec3 o = p.toLocal(eye);
            Vec3 d = p.dirToLocal(dir);
            for (Solid s : p.solids()) {
                double[] r = s.clipNormal(o.x, o.y, o.z, d.x, d.y, d.z);
                if (r == null || r[0] > reach) continue;
                if (best == null || r[0] < best.dist()) {
                    double[] n = Geo.rotY(r[1], r[3], p.yaw());
                    best = new Hit(p, r[0], eye.add(dir.scale(r[0])), new Vec3(n[0], r[2], n[1]));
                }
            }
        }
        return best;
    }
}
