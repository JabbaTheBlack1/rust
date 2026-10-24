package com.rustbuild.build;

import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Связи между элементами по сокетам: кто на ком стоит (base), какая стена под полом (below),
 * соседние плиты одного уровня (adj). Нужны для расчёта устойчивости.
 */
public final class Links {
    private static final double POS_EPS = 0.18;
    private static final double DIR_EPS = 8.0;

    private enum Rel { NONE, BASE, BELOW, ADJ }

    private Links() {}

    private static boolean near(Piece.WorldSocket a, Piece.WorldSocket b) {
        return a.pos().distanceTo(b.pos()) < POS_EPS;
    }

    /** Как элемент a зависит от элемента b. */
    private static Rel rel(Piece a, Piece b) {
        List<Piece.WorldSocket> sa = a.worldSockets(), sb = b.worldSockets();
        PieceType ta = a.type(), tb = b.type();
        for (Piece.WorldSocket x : sa) {
            for (Piece.WorldSocket y : sb) {
                if (!near(x, y)) continue;
                if (x.kind() == Socket.Kind.BASE_PLUG && ta.isWall() && tb.isWall() && y.kind() == Socket.Kind.WALL_TOP) {
                    return Rel.BASE;     // стена стоит на верхе другой стены
                }
                if (x.kind() == Socket.Kind.BASE_PLUG && tb.isSlab()) {
                    if (ta.isWall() || ta.family == PieceType.Family.STEPS) {
                        if (y.kind() == Socket.Kind.EDGE_TOP) return Rel.BASE;
                    } else if (ta.isStairs() && y.kind() == Socket.Kind.CELL) {
                        return Rel.BASE;
                    }
                }
                if (x.kind() == Socket.Kind.EDGE_BOTTOM && ta.isSlabLike() && y.kind() == Socket.Kind.WALL_TOP
                        && Geo.diff(x.dir(), y.dir() + 180) < DIR_EPS) {
                    return Rel.BELOW;
                }
                if (x.kind() == Socket.Kind.EDGE_TOP && y.kind() == Socket.Kind.EDGE_TOP && ta.isSlab() && tb.isSlab()
                        && Geo.diff(x.dir(), y.dir() + 180) < DIR_EPS) {
                    return Rel.ADJ;
                }
            }
        }
        return Rel.NONE;
    }

    private static void add(List<UUID> l, UUID id) { if (!l.contains(id)) l.add(id); }

    /** Пересчитывает связи нового элемента со всеми соседями и записывает их в данные. */
    public static void connect(BuildingData data, Piece p) {
        AABB box = p.worldBounds().inflate(1.0);
        Piece cur = data.getPiece(p.id());
        if (cur == null) return;
        List<UUID> base = new ArrayList<>(cur.base()), below = new ArrayList<>(cur.below());
        List<UUID> adj = new ArrayList<>(cur.adj()), sup = new ArrayList<>(cur.supporting());

        for (Piece q : data.inBox(box)) {
            if (q.id().equals(p.id())) continue;
            Rel r1 = rel(cur, q);
            Rel r2 = rel(q, cur);
            if (r1 == Rel.NONE && r2 == Rel.NONE) continue;
            Piece qq = data.getPiece(q.id());
            if (qq == null) continue;
            List<UUID> qb = new ArrayList<>(qq.base()), qbl = new ArrayList<>(qq.below());
            List<UUID> qa = new ArrayList<>(qq.adj()), qs = new ArrayList<>(qq.supporting());
            switch (r1) {
                case BASE -> { add(base, q.id()); add(qs, p.id()); }
                case BELOW -> { add(below, q.id()); add(qs, p.id()); }
                case ADJ -> { add(adj, q.id()); add(qa, p.id()); }
                default -> { }
            }
            switch (r2) {
                case BASE -> { add(qb, p.id()); add(sup, q.id()); }
                case BELOW -> { add(qbl, p.id()); add(sup, q.id()); }
                case ADJ -> { add(qa, p.id()); add(adj, q.id()); }
                default -> { }
            }
            data.put(qq.withLinks(qb, qbl, qa, qs));
        }
        data.put(cur.withLinks(base, below, adj, sup));
    }

    /** Убирает элемент из связей всех соседей. */
    public static void disconnect(BuildingData data, Piece p) {
        List<UUID> all = new ArrayList<>();
        all.addAll(p.base()); all.addAll(p.below()); all.addAll(p.adj()); all.addAll(p.supporting());
        for (UUID id : all) {
            Piece q = data.getPiece(id);
            if (q == null) continue;
            data.put(q.withLinks(without(q.base(), p.id()), without(q.below(), p.id()),
                    without(q.adj(), p.id()), without(q.supporting(), p.id())));
        }
    }

    private static List<UUID> without(List<UUID> l, UUID id) {
        List<UUID> out = new ArrayList<>(l);
        out.remove(id);
        return out;
    }
}
