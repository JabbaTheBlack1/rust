package com.rustbuild.build;

import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Collection;

/** Луч против тел элементов (в их локальных координатах). */
public final class PieceRaycast {
    public record Hit(Piece piece, double dist, Vec3 point) {}

    private PieceRaycast() {}

    @Nullable
    public static Hit pick(Collection<Piece> pieces, Vec3 eye, Vec3 dir, double reach) {
        Hit best = null;
        for (Piece p : pieces) {
            if (p.pos().distanceTo(eye) > reach + 8.0) continue;
            Vec3 o = p.toLocal(eye);
            Vec3 d = p.dirToLocal(dir);
            for (Solid s : p.solids()) {
                double t = s.clip(o.x, o.y, o.z, d.x, d.y, d.z);
                if (Double.isNaN(t) || t > reach) continue;
                if (best == null || t < best.dist()) best = new Hit(p, t, eye.add(dir.scale(t)));
            }
        }
        return best;
    }
}
