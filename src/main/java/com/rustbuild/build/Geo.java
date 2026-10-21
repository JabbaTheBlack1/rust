package com.rustbuild.build;

/**
 * Геометрия поворотов. Угол a - направление (sin a, cos a) в плоскости XZ: 0 = +Z, 90 = +X.
 * Поворот на phi совпадает с Axis.YP.rotationDegrees(phi) в рендере.
 */
public final class Geo {
    private Geo() {}

    public static double[] rotY(double x, double z, double deg) {
        double r = Math.toRadians(deg), c = Math.cos(r), s = Math.sin(r);
        return new double[]{x * c + z * s, z * c - x * s};
    }

    public static double angle(double dx, double dz) { return Math.toDegrees(Math.atan2(dx, dz)); }

    public static double norm(double a) {
        a = a % 360.0;
        if (a >= 180.0) a -= 360.0;
        if (a < -180.0) a += 360.0;
        return a;
    }

    public static double diff(double a, double b) { return Math.abs(norm(a - b)); }
}
