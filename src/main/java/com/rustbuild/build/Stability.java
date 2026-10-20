package com.rustbuild.build;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Устойчивость построек как в Rust.
 *
 *  - Фундамент = 100% (корень).
 *  - Стена/проём/окно = 92% от плиты под ней. Лестницы и ступени = 95% от плиты под ними.
 *  - Пол/крыша = сумма (50% от устойчивости каждой стены под краем + 25% от каждой соседней плиты
 *    на том же уровне), максимум 100%. Плита прямо на земле получает 90% от грунта.
 *  - Всё, что опустилось ниже 5%, обрушивается. Поэтому консоль держится максимум на 2 клетки,
 *    а при уничтожении стены/фундамента всё, что на них держалось, падает следом.
 */
public final class Stability {
    public static final float COLLAPSE = 0.05f;
    public static final float PLACE_MIN = 0.10f;

    private record Node(Piece piece, List<Piece> walls, List<Piece> adjacent, List<Piece> base,
                        float ground, float maxFactor) {}

    private Stability() {}

    /** Связная группа элементов, достижимая от заданных (по соседним клеткам). */
    public static List<Piece> component(BuildingData data, Collection<Piece> seeds, Collection<Piece> extra) {
        Map<String, Piece> seen = new LinkedHashMap<>();
        Deque<Piece> queue = new ArrayDeque<>();
        for (Piece s : seeds) {
            for (Piece n : data.neighbors(s)) {
                if (seen.putIfAbsent(n.key(), n) == null) queue.add(n);
            }
        }
        for (Piece e : extra) {
            if (seen.putIfAbsent(e.key(), e) == null) queue.add(e);
        }
        while (!queue.isEmpty()) {
            Piece c = queue.poll();
            for (Piece n : data.neighbors(c)) {
                if (seen.putIfAbsent(n.key(), n) == null) queue.add(n);
            }
        }
        return new ArrayList<>(seen.values());
    }

    private static Node buildNode(ServerLevel level, Map<BlockPos, List<Piece>> at, Piece p) {
        List<Piece> walls = new ArrayList<>(), adjacent = new ArrayList<>(), base = new ArrayList<>();
        float ground = 0f;
        float maxFactor = p.type().isWall() ? 0.92f : 0.95f;

        if (p.type().isSlabLike()) {
            Set<String> seenWalls = new HashSet<>();
            int solid = 0;
            for (BlockPos below : p.perimeterBelow()) {
                List<Piece> ps = at.get(below);
                if (ps == null) {
                    if (!level.getBlockState(below).canBeReplaced()) solid++;
                    continue;
                }
                for (Piece o : ps) {
                    if (o.type().isWall() && !o.type().isLowWall() && seenWalls.add(o.key())) walls.add(o);
                }
            }
            if (solid >= 6) ground = 0.9f;

            Set<String> seenAdj = new HashSet<>();
            for (BlockPos pos : p.borderPositions()) {
                List<Piece> ps = at.get(pos);
                if (ps == null) continue;
                for (Piece o : ps) {
                    if (!o.key().equals(p.key()) && o.type().isSlabLike() && o.baseY() == p.baseY()
                            && seenAdj.add(o.key())) {
                        adjacent.add(o);
                    }
                }
            }
        } else {
            Set<String> seenBase = new HashSet<>();
            for (BlockPos pos : p.basePositions()) {
                List<Piece> ps = at.get(pos);
                if (ps == null) continue;
                for (Piece o : ps) {
                    if (o.type().isSlab() && seenBase.add(o.key())) base.add(o);
                }
            }
        }
        return new Node(p, walls, adjacent, base, ground, maxFactor);
    }

    /** Считает устойчивость (0..1) для каждого элемента группы. */
    public static Map<String, Float> compute(ServerLevel level, List<Piece> comp) {
        Map<BlockPos, List<Piece>> at = new HashMap<>();
        for (Piece p : comp) {
            for (BlockEntry e : p.entries()) at.computeIfAbsent(e.pos(), k -> new ArrayList<>(2)).add(p);
        }
        Map<String, Float> stab = new HashMap<>();
        List<Node> nodes = new ArrayList<>();
        for (Piece p : comp) {
            stab.put(p.key(), p.type().isRoot() ? 1f : 0f);
            nodes.add(buildNode(level, at, p));
        }

        for (int it = 0; it < 48; it++) {
            boolean changed = false;
            for (Node n : nodes) {
                Piece p = n.piece();
                if (p.type().isRoot()) continue;
                float v;
                if (p.type().isSlabLike()) {
                    v = n.ground();
                    for (Piece w : n.walls()) v += 0.5f * stab.get(w.key());
                    for (Piece a : n.adjacent()) v += 0.25f * stab.get(a.key());
                    v = Math.min(1f, v);
                } else {
                    float best = 0f;
                    for (Piece b : n.base()) best = Math.max(best, stab.get(b.key()));
                    v = n.maxFactor() * best;
                }
                if (Math.abs(v - stab.get(p.key())) > 0.0005f) {
                    stab.put(p.key(), v);
                    changed = true;
                }
            }
            if (!changed) break;
        }
        return stab;
    }

    public static float stabilityOf(ServerLevel level, BuildingData data, Piece piece) {
        if (piece.type().isRoot()) return 1f;
        List<Piece> comp = component(data, List.of(piece), List.of(piece));
        Float s = compute(level, comp).get(piece.key());
        return s == null ? 0f : s;
    }

    /** Можно ли поставить элемент: его устойчивость не должна быть почти нулевой. */
    public static boolean placementStable(ServerLevel level, BuildingData data, Piece piece) {
        return stabilityOf(level, data, piece) >= PLACE_MIN;
    }

    /**
     * Проверяет постройку вокруг только что исчезнувших элементов и обрушивает всё, что потеряло опору.
     * Повторяется, пока не останется неустойчивых элементов (цепная реакция).
     */
    public static void collapseAround(ServerLevel level, BuildingData data, Collection<Piece> removed) {
        Collection<Piece> seeds = removed;
        boolean any = false;
        BlockPos soundAt = null;
        for (int round = 0; round < 16; round++) {
            List<Piece> comp = component(data, seeds, List.of());
            if (comp.isEmpty()) break;
            Map<String, Float> stab = compute(level, comp);
            List<Piece> bad = new ArrayList<>();
            for (Piece p : comp) {
                if (stab.getOrDefault(p.key(), 0f) < COLLAPSE) bad.add(p);
            }
            if (bad.isEmpty()) break;
            for (Piece b : bad) {
                if (soundAt == null) soundAt = b.center();
                BuildActions.destroyRaw(level, data, b);
            }
            any = true;
            seeds = bad;
        }
        if (any && soundAt != null) {
            level.playSound(null, soundAt, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 2.0f, 0.5f);
        }
    }
}
