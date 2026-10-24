package com.rustbuild.build;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Положение объекта в мире: точка и угол поворота вокруг вертикали (любой). */
public record Xf(double x, double y, double z, double yaw) {
    public Vec3 pos() { return new Vec3(x, y, z); }

    public Vec3 toWorld(double lx, double ly, double lz) {
        double[] r = Geo.rotY(lx, lz, yaw);
        return new Vec3(x + r[0], y + ly, z + r[1]);
    }

    public Vec3 toLocal(Vec3 w) {
        double[] r = Geo.rotY(w.x - x, w.z - z, -yaw);
        return new Vec3(r[0], w.y - y, r[1]);
    }

    public Vec3 dirToLocal(Vec3 d) {
        double[] r = Geo.rotY(d.x, d.z, -yaw);
        return new Vec3(r[0], d.y, r[1]);
    }

    public AABB bounds(List<Solid> solids) {
        double minX = 1e9, minY = 1e9, minZ = 1e9, maxX = -1e9, maxY = -1e9, maxZ = -1e9;
        for (Solid s : solids) {
            AABB b = s.bounds();
            for (int i = 0; i < 8; i++) {
                Vec3 w = toWorld((i & 1) == 0 ? b.minX : b.maxX, (i & 2) == 0 ? b.minY : b.maxY, (i & 4) == 0 ? b.minZ : b.maxZ);
                minX = Math.min(minX, w.x); maxX = Math.max(maxX, w.x);
                minY = Math.min(minY, w.y); maxY = Math.max(maxY, w.y);
                minZ = Math.min(minZ, w.z); maxZ = Math.max(maxZ, w.z);
            }
        }
        if (minX > maxX) return new AABB(x - 0.25, y, z - 0.25, x + 0.25, y + 0.5, z + 0.25);
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
