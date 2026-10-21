package com.rustbuild.build;

import net.minecraft.world.phys.AABB;

import java.util.List;

/** Выпуклое тело = пересечение полупространств n*p <= d (нормали единичные). Нужно для коллизий и лучей. */
public final class Solid {
    private final double[][] planes;
    private final AABB bounds;

    private Solid(double[][] planes, AABB bounds) {
        this.planes = planes;
        this.bounds = bounds;
    }

    public AABB bounds() { return bounds; }

    public static double[] plane(double nx, double ny, double nz, double d) {
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        return new double[]{nx / len, ny / len, nz / len, d / len};
    }

    public static Solid of(double[][] planes, AABB bounds) { return new Solid(planes, bounds); }

    public static Solid box(double x0, double y0, double z0, double x1, double y1, double z1) {
        double[][] p = {
                {1, 0, 0, x1}, {-1, 0, 0, -x0},
                {0, 1, 0, y1}, {0, -1, 0, -y0},
                {0, 0, 1, z1}, {0, 0, -1, -z0}};
        return new Solid(p, new AABB(x0, y0, z0, x1, y1, z1));
    }

    /** Призма над выпуклым многоугольником (x,z), по высоте от y0 до y1. */
    public static Solid prism(List<double[]> poly, double y0, double y1) {
        int n = poly.size();
        double cx = 0, cz = 0;
        for (double[] v : poly) { cx += v[0]; cz += v[1]; }
        cx /= n; cz /= n;
        double[][] p = new double[n + 2][];
        double minX = 1e9, maxX = -1e9, minZ = 1e9, maxZ = -1e9;
        for (int i = 0; i < n; i++) {
            double[] a = poly.get(i), b = poly.get((i + 1) % n);
            double nx = b[1] - a[1], nz = -(b[0] - a[0]);
            if (nx * (a[0] - cx) + nz * (a[1] - cz) < 0) { nx = -nx; nz = -nz; }
            double len = Math.sqrt(nx * nx + nz * nz);
            nx /= len; nz /= len;
            p[i] = new double[]{nx, 0, nz, nx * a[0] + nz * a[1]};
            minX = Math.min(minX, a[0]); maxX = Math.max(maxX, a[0]);
            minZ = Math.min(minZ, a[1]); maxZ = Math.max(maxZ, a[1]);
        }
        p[n] = new double[]{0, 1, 0, y1};
        p[n + 1] = new double[]{0, -1, 0, -y0};
        return new Solid(p, new AABB(minX, y0, minZ, maxX, y1, maxZ));
    }

    /** margin > 0 сжимает тело, margin < 0 раздувает. */
    public boolean contains(double x, double y, double z, double margin) {
        for (double[] p : planes) {
            if (p[0] * x + p[1] * y + p[2] * z > p[3] - margin) return false;
        }
        return true;
    }

    /** Расстояние вдоль луча до входа в тело или NaN. */
    public double clip(double ox, double oy, double oz, double dx, double dy, double dz) {
        double tIn = Double.NEGATIVE_INFINITY, tOut = Double.POSITIVE_INFINITY;
        for (double[] p : planes) {
            double denom = p[0] * dx + p[1] * dy + p[2] * dz;
            double num = p[3] - (p[0] * ox + p[1] * oy + p[2] * oz);
            if (Math.abs(denom) < 1e-9) {
                if (num < 0) return Double.NaN;
                continue;
            }
            double t = num / denom;
            if (denom > 0) tOut = Math.min(tOut, t);
            else tIn = Math.max(tIn, t);
        }
        if (tIn > tOut || tOut < 0) return Double.NaN;
        return Math.max(tIn, 0.0);
    }
}
