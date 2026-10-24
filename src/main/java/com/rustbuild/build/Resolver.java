package com.rustbuild.build;

import com.rustbuild.block.CollisionBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
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

/**
 * Определяет, ЧТО и КУДА будет поставлено. Как в Rust:
 *  - первый фундамент ставится свободно в точке прицела, под любым углом;
 *  - всё остальное притягивается к сокетам (краям, верхам стен, клеткам) уже стоящих элементов.
 * Считается одинаково на клиенте (контур) и на сервере (постановка); элементы берутся из provider.
 */
public final class Resolver {
    private static final double REACH = 10.0;
    private static final double SNAP_DIST = 4.2;

    private Resolver() {}

    private record Cand(Piece piece, double score) {}

    @Nullable
    public static Placement resolve(Level level, Player player, PieceType type, int rotDeg,
                                    Function<AABB, List<Piece>> provider) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(REACH));

        PieceRaycast.Hit ph = PieceRaycast.pick(provider.apply(new AABB(eye, end).inflate(4.0)), eye, look, REACH);
        BlockHitResult th = terrainRay(level, eye, end);
        if (ph == null && th == null) return null;

        double dt = th == null ? Double.MAX_VALUE : th.getLocation().distanceTo(eye);
        double dp = ph == null ? Double.MAX_VALUE : ph.dist();
        Vec3 aim;
        boolean terrainUp = false;
        if (dp < dt) {
            aim = ph.point();
        } else {
            aim = th.getLocation();
            terrainUp = th.getDirection() == Direction.UP;
        }

        List<Piece> near = provider.apply(new AABB(aim, aim).inflate(8.0));

        Cand best = null;
        for (Piece q : near) {
            for (Cand c : candidates(type, q, look, aim)) {
                if (best == null || c.score() < best.score()) best = c;
            }
        }

        Piece piece;
        if (best != null && best.score() <= SNAP_DIST) {
            piece = best.piece();
        } else if (type.family == PieceType.Family.FOUNDATION && terrainUp) {
            double top = Math.round((aim.y + 0.5) * 4.0) / 4.0;
            double yaw = Math.round(-player.getYRot() + rotDeg);
            piece = Piece.blank(type, aim.x, top, aim.z, yaw, 0.5f);
        } else {
            return null;
        }
        return finish(level, piece, near);
    }

    // ------------------------------------------------------------------ кандидаты
    private static List<Cand> candidates(PieceType type, Piece q, Vec3 look, Vec3 aim) {
        List<Cand> out = new ArrayList<>();
        for (Piece.WorldSocket s : q.worldSockets()) {
            Piece p = null;
            switch (type.family) {
                case FOUNDATION -> {
                    if (s.kind() == Socket.Kind.EDGE_TOP && q.type().isRoot()) p = alignSlab(type, s, Socket.Kind.EDGE_TOP);
                }
                case FLOOR -> {
                    if (s.kind() == Socket.Kind.WALL_TOP) p = alignSlab(type, s, Socket.Kind.EDGE_BOTTOM);
                    else if (s.kind() == Socket.Kind.EDGE_TOP && q.type().isSlab()) p = alignSlab(type, s, Socket.Kind.EDGE_TOP);
                }
                case ROOF -> {
                    if (s.kind() == Socket.Kind.WALL_TOP) p = alignSlab(type, s, Socket.Kind.EDGE_BOTTOM);
                }
                case WALL -> {
                    if (s.kind() == Socket.Kind.EDGE_TOP && q.type().isSlab()) {
                        p = Piece.blank(type, s.pos().x, s.pos().y, s.pos().z, s.dir(), 0f);
                    }
                }
                case STEPS -> {
                    if (s.kind() == Socket.Kind.EDGE_TOP && q.type().isRoot()) {
                        p = Piece.blank(type, s.pos().x, s.pos().y, s.pos().z, s.dir(), 0f);
                    }
                }
                case STAIRS -> {
                    if (s.kind() == Socket.Kind.CELL) {
                        double view = Geo.angle(look.x, look.z), bestYaw = q.yaw(), bestDiff = 1e9;
                        for (int k = 0; k < 4; k++) {
                            double yaw = q.yaw() + 90.0 * k;
                            double d = Geo.diff(yaw, view);
                            if (d < bestDiff) { bestDiff = d; bestYaw = yaw; }
                        }
                        p = Piece.blank(type, s.pos().x, s.pos().y, s.pos().z, bestYaw, 0f);
                    }
                }
            }
            if (p != null) {
                double[] a = PieceDef.anchor(type);
                Vec3 center = p.toWorld(a[0], a[1], a[2]);
                out.add(new Cand(p, center.distanceTo(aim)));
            }
        }
        return out;
    }

    /** Совмещает стыковочный сокет нового элемента с сокетом s: сокеты смотрят друг на друга. */
    private static Piece alignSlab(PieceType type, Piece.WorldSocket s, Socket.Kind plugKind) {
        Socket plug = PieceDef.plug(type, plugKind);
        if (plug == null) return null;
        double yaw = Geo.norm(s.dir() + 180.0 - plug.dir());
        double[] r = Geo.rotY(plug.x(), plug.z(), yaw);
        return Piece.blank(type, s.pos().x - r[0], s.pos().y - plug.y(), s.pos().z - r[1], yaw, 0f);
    }

    // ------------------------------------------------------------------ проверки
    private static Placement finish(Level level, Piece piece, List<Piece> near) {
        String reason = null;
        if (piece.type().isRoot()) {
            float depth = computeDepth(level, piece);
            if (depth < 0) reason = "no_ground";
            else piece = piece.withDepth(depth);
        }
        if (reason == null) reason = check(level, piece, near);
        return new Placement(piece, reason == null, reason);
    }

    @Nullable
    private static String check(Level level, Piece p, List<Piece> near) {
        boolean foundation = p.type().isRoot();
        List<Vec3> samples = samples(p, foundation);

        for (Piece q : near) {
            if (q.id().equals(p.id()) && !p.id().equals(net.minecraft.Util.NIL_UUID)) continue;
            if (q.pos().distanceTo(p.pos()) > 8.0) continue;
            if (p.type().isWall() && q.type().isWall()) {
                // стены разных рёбер сходятся в углах - это нормально; запрещён только дубликат на том же ребре
                if (q.pos().distanceTo(p.pos()) < 0.3 && Geo.diff(Geo.norm(q.yaw() * 2), Geo.norm(p.yaw() * 2)) < 8.0) return "blocked";
                continue;
            }
            List<Solid> qs = q.solids();
            for (Vec3 w : samples) {
                Vec3 l = q.toLocal(w);
                for (Solid s : qs) if (s.contains(l.x, l.y, l.z, 0.0)) return "blocked";
            }
        }

        for (Vec3 w : samples) {
            BlockPos bp = BlockPos.containing(w);
            BlockState st = level.getBlockState(bp);
            if (!st.getFluidState().isEmpty() && (!foundation || w.y > p.y() - 0.3)) return "water";
            if (foundation || st.getBlock() instanceof CollisionBlock) continue;
            VoxelShape sh = st.getCollisionShape(level, bp);
            if (!sh.isEmpty() && sh.bounds().move(bp).contains(w)) return "blocked";
        }
        return null;
    }

    /** Точки внутри тела элемента (мировые) для проверок пересечений. */
    private static List<Vec3> samples(Piece p, boolean topOnly) {
        List<Vec3> out = new ArrayList<>();
        double step = p.type().isStairs() ? 0.45 : 0.32;
        for (Solid s : p.solids()) {
            AABB b = s.bounds();
            double y0 = topOnly ? Math.max(b.minY, -0.45) : b.minY;
            for (double lx : axis(b.minX, b.maxX, step)) {
                for (double ly : axis(y0, b.maxY, step)) {
                    for (double lz : axis(b.minZ, b.maxZ, step)) {
                        if (s.contains(lx, ly, lz, 0.04)) out.add(p.toWorld(lx, ly, lz));
                    }
                }
            }
        }
        return out;
    }

    private static double[] axis(double min, double max, double step) {
        double a = min + 0.05, b = max - 0.05;
        if (b <= a) return new double[]{(min + max) / 2};
        int n = Math.max(1, (int) Math.ceil((b - a) / step));
        double[] r = new double[n + 1];
        for (int i = 0; i <= n; i++) r[i] = a + (b - a) * i / n;
        return r;
    }

    // ------------------------------------------------------------------ земля под фундаментом
    private static float computeDepth(Level level, Piece p) {
        double top = p.y();
        double min = top;
        List<double[]> pts = new ArrayList<>();
        List<double[]> poly = PieceDef.footprint(p.type());
        double cx = 0, cz = 0;
        for (double[] v : poly) { pts.add(v); cx += v[0]; cz += v[1]; }
        pts.add(new double[]{cx / poly.size(), cz / poly.size()});
        for (int i = 0; i < poly.size(); i++) {
            double[] a = poly.get(i), b = poly.get((i + 1) % poly.size());
            pts.add(new double[]{(a[0] + b[0]) / 2, (a[1] + b[1]) / 2});
            pts.add(new double[]{(a[0] + b[0]) / 2 * 0.5, (a[1] + b[1]) / 2 * 0.5});
        }
        for (double[] q : pts) {
            Vec3 w = p.toWorld(q[0], 0, q[1]);
            double g = groundTop(level, w.x, w.z, top);
            if (Double.isNaN(g)) return -1f;
            min = Math.min(min, g);
        }
        double depth = Math.max(0.5, top - min + 0.25);
        return depth > 12.0 ? -1f : (float) depth;
    }

    private static double groundTop(Level level, double x, double z, double top) {
        int startY = (int) Math.floor(top) + 2;
        for (int y = startY; y >= startY - 30; y--) {
            BlockPos bp = new BlockPos((int) Math.floor(x), y, (int) Math.floor(z));
            BlockState st = level.getBlockState(bp);
            if (st.getBlock() instanceof CollisionBlock) continue;
            VoxelShape sh = st.getCollisionShape(level, bp);
            if (!sh.isEmpty()) return y + sh.max(Direction.Axis.Y);
        }
        return Double.NaN;
    }

    /** Луч по твёрдой земле: невидимые блоки-коллайдеры элементов пропускаются. */
    @Nullable
    public static BlockHitResult terrainRay(Level level, Vec3 from, Vec3 to) {
        return BlockGetter.traverseBlocks(from, to, level, (lvl, pos) -> {
            BlockState st = lvl.getBlockState(pos);
            if (st.getBlock() instanceof CollisionBlock) return null;
            VoxelShape shape = st.getCollisionShape(lvl, pos);
            return shape.isEmpty() ? null : shape.clip(from, to, pos);
        }, ctx -> null);
    }
}
