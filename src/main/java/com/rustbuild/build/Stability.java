package com.rustbuild.build;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Устойчивость построек как в Rust.
 *  - Фундамент = 100% (корень).
 *  - Стена = 92% от плиты под ней; лестницы и ступени = 95%.
 *  - Пол/крыша = 50% от каждой стены под краем + 25% от каждой соседней плиты, максимум 100%.
 *  - Ниже 5% - элемент обрушивается, цепной реакцией.
 */
public final class Stability {
    public static final float COLLAPSE = 0.05f;
    public static final float PLACE_MIN = 0.10f;

    private Stability() {}

    private static List<UUID> neighbors(Piece p) {
        List<UUID> all = new ArrayList<>();
        all.addAll(p.base()); all.addAll(p.below()); all.addAll(p.adj()); all.addAll(p.supporting());
        return all;
    }

    /** Связная группа элементов, достижимая от заданных по графу связей. */
    public static List<Piece> component(BuildingData data, Collection<UUID> seeds) {
        Map<UUID, Piece> seen = new LinkedHashMap<>();
        Deque<UUID> queue = new ArrayDeque<>(seeds);
        while (!queue.isEmpty()) {
            UUID id = queue.poll();
            if (seen.containsKey(id)) continue;
            Piece p = data.getPiece(id);
            if (p == null) continue;
            seen.put(id, p);
            queue.addAll(neighbors(p));
        }
        return new ArrayList<>(seen.values());
    }

    public static Map<UUID, Float> compute(List<Piece> comp) {
        Map<UUID, Float> stab = new HashMap<>();
        for (Piece p : comp) stab.put(p.id(), p.type().isRoot() ? 1f : 0f);

        for (int it = 0; it < 48; it++) {
            boolean changed = false;
            for (Piece p : comp) {
                if (p.type().isRoot()) continue;
                float v;
                if (p.type().isSlabLike()) {
                    v = 0f;
                    for (UUID w : p.below()) v += 0.5f * stab.getOrDefault(w, 0f);
                    for (UUID a : p.adj()) v += 0.25f * stab.getOrDefault(a, 0f);
                    v = Math.min(1f, v);
                } else {
                    float best = 0f;
                    for (UUID b : p.base()) best = Math.max(best, stab.getOrDefault(b, 0f));
                    v = (p.type().isWall() ? 0.92f : 0.95f) * best;
                }
                if (Math.abs(v - stab.get(p.id())) > 0.0005f) {
                    stab.put(p.id(), v);
                    changed = true;
                }
            }
            if (!changed) break;
        }
        return stab;
    }

    public static float stabilityOf(BuildingData data, Piece piece) {
        if (piece.type().isRoot()) return 1f;
        Float s = compute(component(data, List.of(piece.id()))).get(piece.id());
        return s == null ? 0f : s;
    }

    /** Проверяет постройку вокруг исчезнувших элементов и обрушивает всё, что потеряло опору. */
    public static void collapseAround(ServerLevel level, BuildingData data, Collection<UUID> seeds) {
        BlockPos soundAt = null;
        Collection<UUID> cur = seeds;
        for (int round = 0; round < 16; round++) {
            List<Piece> comp = component(data, cur);
            if (comp.isEmpty()) break;
            Map<UUID, Float> stab = compute(comp);
            List<Piece> bad = new ArrayList<>();
            for (Piece p : comp) {
                if (stab.getOrDefault(p.id(), 0f) < COLLAPSE) bad.add(p);
            }
            if (bad.isEmpty()) break;
            List<UUID> next = new ArrayList<>();
            for (Piece b : bad) {
                if (soundAt == null) soundAt = b.blockPos();
                next.addAll(neighbors(b));
                BuildActions.destroyRaw(level, data, b);
            }
            cur = next;
        }
        if (soundAt != null) level.playSound(null, soundAt, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 2.0f, 0.5f);
    }
}
