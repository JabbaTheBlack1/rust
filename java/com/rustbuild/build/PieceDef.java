package com.rustbuild.build;

import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Каталог геометрии элементов (в блоках = метрах Rust).
 *
 * Локальные системы координат:
 *  - плиты (фундамент/пол): начало в центре верхней поверхности, y=0 - верх;
 *  - стены: начало в центре низа стены (на верхе плиты), длина вдоль X, толщина вдоль Z;
 *  - лестницы: начало в центре клетки на уровне плиты, подъём к +Z;
 *  - крыша: начало в середине нижнего края, скат уходит к +Z;
 *  - ступени фундамента: начало на верхнем краю фундамента, ступень уходит наружу к +Z.
 */
public final class PieceDef {
    public static final double S = 3.0;                 // сторона клетки
    public static final double T = 0.25;                // толщина пола/стены
    public static final double WALL_H = 2.75;           // высота стены (этаж = 3.0 с полом)
    public static final double LOW_H = 1.5;
    public static final double LEVEL = 3.0;
    public static final double H3 = S * Math.sqrt(3.0) / 2.0;   // высота равностороннего треугольника
    public static final double ROOF_TAN = Math.tan(Math.toRadians(22.5));
    private static final double E = 1.375;              // половина клетки минус полтолщины стены

    private PieceDef() {}

    // ------------------------------------------------------------------ контуры плит
    /** Вершины контура плиты. Ребро 0 (v0 -> v1) всегда смотрит наружу к +Z. */
    public static List<double[]> footprint(PieceType t) {
        List<double[]> v = new ArrayList<>();
        if (t.isTriangle()) {
            v.add(new double[]{-S / 2, H3 / 3});
            v.add(new double[]{S / 2, H3 / 3});
            v.add(new double[]{0, -2 * H3 / 3});
        } else {
            v.add(new double[]{-S / 2, S / 2});
            v.add(new double[]{S / 2, S / 2});
            v.add(new double[]{S / 2, -S / 2});
            v.add(new double[]{-S / 2, -S / 2});
        }
        return v;
    }

    // ------------------------------------------------------------------ тела (коллизия, лучи, воксели)
    public static List<Solid> solids(PieceType t, float depth) {
        List<Solid> out = new ArrayList<>();
        double h = S / 2;
        switch (t) {
            case FOUNDATION, TRIANGLE_FOUNDATION -> out.add(Solid.prism(footprint(t), -Math.max(depth, 0.5f), 0));
            case FLOOR, TRIANGLE_FLOOR -> out.add(Solid.prism(footprint(t), -T, 0));
            case FLOOR_FRAME -> {
                double g = 0.75;
                out.add(Solid.box(-h, -T, -h, h, 0, -g));
                out.add(Solid.box(-h, -T, g, h, 0, h));
                out.add(Solid.box(-h, -T, -g, -g, 0, g));
                out.add(Solid.box(g, -T, -g, h, 0, g));
            }
            case WALL -> out.add(Solid.box(-h, 0, -T / 2, h, WALL_H, T / 2));
            case LOW_WALL -> out.add(Solid.box(-h, 0, -T / 2, h, LOW_H, T / 2));
            case DOORWAY -> {
                out.add(Solid.box(-h, 0, -T / 2, -0.5, WALL_H, T / 2));
                out.add(Solid.box(0.5, 0, -T / 2, h, WALL_H, T / 2));
                out.add(Solid.box(-0.5, 2.0, -T / 2, 0.5, WALL_H, T / 2));
            }
            case WINDOW -> {
                out.add(Solid.box(-h, 0, -T / 2, -0.5, WALL_H, T / 2));
                out.add(Solid.box(0.5, 0, -T / 2, h, WALL_H, T / 2));
                out.add(Solid.box(-0.5, 0, -T / 2, 0.5, 1.0, T / 2));
                out.add(Solid.box(-0.5, 2.0, -T / 2, 0.5, WALL_H, T / 2));
            }
            case ROOF -> out.add(roof(false));
            case TRIANGLE_ROOF -> out.add(roof(true));
            case STAIRS -> {
                for (int k = 0; k < 12; k++) {
                    out.add(Solid.box(-1.0, 0, -1.5 + 0.25 * k, 1.0, 0.25 * (k + 1), -1.5 + 0.25 * (k + 1)));
                }
            }
            case L_STAIRS -> {
                for (int k = 0; k < 6; k++) {
                    out.add(Solid.box(-E, 0, -1.5 + 0.25 * k, 0, 0.25 * (k + 1), -1.5 + 0.25 * (k + 1)));
                    out.add(Solid.box(0.25 * k, 0, 0, 0.25 * (k + 1), 1.5 + 0.25 * (k + 1), E));
                }
                out.add(Solid.box(-E, 0, 0, 0, 1.5, E));
            }
            case U_STAIRS -> {
                for (int k = 0; k < 6; k++) {
                    out.add(Solid.box(-E, 0, -1.5 + 0.25 * k, 0, 0.25 * (k + 1), -1.5 + 0.25 * (k + 1)));
                    out.add(Solid.box(0, 0, -0.25 * (k + 1), E, 1.5 + 0.25 * (k + 1), -0.25 * k));
                }
                out.add(Solid.box(-E, 0, 0, E, 1.5, E));
            }
            case FOUNDATION_STEPS -> out.add(Solid.box(-1.0, -0.75, 0, 1.0, -0.25, 0.6));
        }
        return out;
    }

    /** Наклонная плита крыши: скат поднимается к +Z под 22.5 градуса. */
    private static Solid roof(boolean triangle) {
        double tan = ROOF_TAN;
        double run = triangle ? H3 : S;
        double top = run * tan + T;
        List<double[]> planes = new ArrayList<>();
        planes.add(Solid.plane(0, -1, tan, 0));          // низ ската
        planes.add(Solid.plane(0, 1, -tan, T));          // верх ската
        planes.add(Solid.plane(0, 0, -1, 0));            // нижний край
        if (triangle) {
            planes.add(Solid.plane(H3, 0, S / 2, H3 * S / 2));
            planes.add(Solid.plane(-H3, 0, S / 2, H3 * S / 2));
        } else {
            planes.add(Solid.plane(0, 0, 1, S));
            planes.add(Solid.plane(1, 0, 0, S / 2));
            planes.add(Solid.plane(-1, 0, 0, S / 2));
        }
        return Solid.of(planes.toArray(new double[0][]), new AABB(-S / 2, 0, 0, S / 2, top, run));
    }

    // ------------------------------------------------------------------ сокеты
    public static List<Socket> sockets(PieceType t) {
        List<Socket> out = new ArrayList<>();
        switch (t.family) {
            case FOUNDATION, FLOOR -> {
                List<double[]> poly = footprint(t);
                for (int i = 0; i < poly.size(); i++) {
                    double[] a = poly.get(i), b = poly.get((i + 1) % poly.size());
                    double mx = (a[0] + b[0]) / 2, mz = (a[1] + b[1]) / 2;
                    double nx = b[1] - a[1], nz = -(b[0] - a[0]);
                    if (nx * mx + nz * mz < 0) { nx = -nx; nz = -nz; }
                    double dir = Geo.angle(nx, nz);
                    out.add(new Socket(Socket.Kind.EDGE_TOP, mx, 0, mz, dir));
                    if (t.family == PieceType.Family.FLOOR) out.add(new Socket(Socket.Kind.EDGE_BOTTOM, mx, -T, mz, dir));
                }
                if (!t.isTriangle() && !t.isFrame()) out.add(new Socket(Socket.Kind.CELL, 0, 0, 0, 0));
            }
            case ROOF -> out.add(new Socket(Socket.Kind.EDGE_BOTTOM, 0, 0, 0, 180));
            case WALL -> {
                out.add(new Socket(Socket.Kind.BASE_PLUG, 0, 0, 0, 0));
                if (!t.isLowWall()) {
                    out.add(new Socket(Socket.Kind.WALL_TOP, 0, WALL_H, 0, 0));
                    out.add(new Socket(Socket.Kind.WALL_TOP, 0, WALL_H, 0, 180));
                }
            }
            case STAIRS, STEPS -> out.add(new Socket(Socket.Kind.BASE_PLUG, 0, 0, 0, 0));
        }
        return out;
    }

    public static Socket plug(PieceType t, Socket.Kind kind) {
        for (Socket s : sockets(t)) if (s.kind() == kind) return s;
        return null;
    }

    /** Локальная точка, по которой выбирается элемент при прицеливании. */
    public static double[] anchor(PieceType t) {
        return switch (t.family) {
            case FOUNDATION, FLOOR -> new double[]{0, -T / 2, 0};
            case WALL -> new double[]{0, WALL_H / 2, 0};
            case STAIRS -> new double[]{0, 1.5, 0};
            case ROOF -> new double[]{0, 0.6, 1.5};
            case STEPS -> new double[]{0, -0.5, 0.5};
        };
    }

    // ------------------------------------------------------------------ контуры для предпросмотра (локальные AABB)
    public static List<AABB> previewBoxes(PieceType t, float depth) {
        List<AABB> out = new ArrayList<>();
        if (t.isTriangle() && t.family != PieceType.Family.ROOF) {
            double y0 = t.family == PieceType.Family.FOUNDATION ? -Math.max(depth, 0.5f) : -T;
            int rows = 8;
            for (int r = 0; r < rows; r++) {
                double z0 = -2 * H3 / 3 + H3 * r / rows, z1 = -2 * H3 / 3 + H3 * (r + 1) / rows;
                double half = (z1 + 2 * H3 / 3) / H3 * S / 2;
                out.add(new AABB(-half, y0, z0, half, 0, z1));
            }
            return out;
        }
        if (t.isStairs()) {
            out.add(new AABB(-E, 0, -1.5, E, LEVEL, 1.5));
            return out;
        }
        for (Solid s : solids(t, depth)) out.add(s.bounds());
        return out;
    }

    /** Проёмы (рисуются оранжевым). */
    public static List<AABB> previewCutouts(PieceType t) {
        List<AABB> out = new ArrayList<>();
        if (t == PieceType.DOORWAY) out.add(new AABB(-0.5, 0, -T / 2, 0.5, 2.0, T / 2));
        if (t == PieceType.WINDOW) out.add(new AABB(-0.5, 1.0, -T / 2, 0.5, 2.0, T / 2));
        if (t == PieceType.FLOOR_FRAME) out.add(new AABB(-0.75, -T, -0.75, 0.75, 0, 0.75));
        return out;
    }
}
