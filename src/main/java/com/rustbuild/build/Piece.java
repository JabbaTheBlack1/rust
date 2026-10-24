package com.rustbuild.build;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Один элемент постройки в свободных координатах: позиция (x,y,z) и угол поворота yaw (любой, в градусах).
 * Связи (base/below/adj/supporting) - граф опор для устойчивости.
 */
public record Piece(UUID id, PieceType type, Tier tier, double x, double y, double z, double yaw, float depth,
                    UUID owner, long created, float hp, long lastDamage,
                    List<UUID> base, List<UUID> below, List<UUID> adj, List<UUID> supporting) {

    public record WorldSocket(Socket.Kind kind, Vec3 pos, double dir) {}

    public static Piece blank(PieceType type, double x, double y, double z, double yaw, float depth) {
        return new Piece(Util.NIL_UUID, type, Tier.TWIG, x, y, z, Geo.norm(yaw), depth, Util.NIL_UUID, 0L,
                Tier.TWIG.hp * type.hpMult, 0L, List.of(), List.of(), List.of(), List.of());
    }

    public Vec3 pos() { return new Vec3(x, y, z); }
    public BlockPos blockPos() { return BlockPos.containing(x, y, z); }
    public float maxHp() { return tier.hp * type.hpMult; }

    // ---- копии
    public Piece withId(UUID i) {
        return new Piece(i, type, tier, x, y, z, yaw, depth, owner, created, hp, lastDamage, base, below, adj, supporting);
    }
    public Piece withTier(Tier t) {
        return new Piece(id, type, t, x, y, z, yaw, depth, owner, created, t.hp * type.hpMult, lastDamage, base, below, adj, supporting);
    }
    public Piece withOwner(UUID o, long time) {
        return new Piece(id, type, tier, x, y, z, yaw, depth, o, time, maxHp(), 0L, base, below, adj, supporting);
    }
    public Piece withDepth(float d) {
        return new Piece(id, type, tier, x, y, z, yaw, d, owner, created, hp, lastDamage, base, below, adj, supporting);
    }
    public Piece withHp(float h, long damageTime) {
        return new Piece(id, type, tier, x, y, z, yaw, depth, owner, created, h, damageTime, base, below, adj, supporting);
    }
    public Piece withLinks(List<UUID> b, List<UUID> bl, List<UUID> a, List<UUID> s) {
        return new Piece(id, type, tier, x, y, z, yaw, depth, owner, created, hp, lastDamage,
                List.copyOf(b), List.copyOf(bl), List.copyOf(a), List.copyOf(s));
    }

    // ---- преобразования
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

    public List<Solid> solids() { return PieceDef.solids(type, depth); }

    public List<WorldSocket> worldSockets() {
        List<WorldSocket> out = new ArrayList<>();
        for (Socket s : PieceDef.sockets(type)) out.add(new WorldSocket(s.kind(), toWorld(s.x(), s.y(), s.z()), Geo.norm(s.dir() + yaw)));
        return out;
    }

    public AABB worldBounds() {
        double minX = 1e9, minY = 1e9, minZ = 1e9, maxX = -1e9, maxY = -1e9, maxZ = -1e9;
        for (Solid s : solids()) {
            AABB b = s.bounds();
            for (int i = 0; i < 8; i++) {
                Vec3 w = toWorld((i & 1) == 0 ? b.minX : b.maxX, (i & 2) == 0 ? b.minY : b.maxY, (i & 4) == 0 ? b.minZ : b.maxZ);
                minX = Math.min(minX, w.x); maxX = Math.max(maxX, w.x);
                minY = Math.min(minY, w.y); maxY = Math.max(maxY, w.y);
                minZ = Math.min(minZ, w.z); maxZ = Math.max(maxZ, w.z);
            }
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    // ---- NBT
    private static ListTag ids(List<UUID> l) {
        ListTag t = new ListTag();
        for (UUID u : l) t.add(StringTag.valueOf(u.toString()));
        return t;
    }

    private static List<UUID> readIds(CompoundTag t, String key) {
        List<UUID> out = new ArrayList<>();
        ListTag l = t.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < l.size(); i++) out.add(UUID.fromString(l.getString(i)));
        return out;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("type", type.name());
        t.putString("tier", tier.name());
        t.putDouble("x", x); t.putDouble("y", y); t.putDouble("z", z);
        t.putDouble("yaw", yaw);
        t.putFloat("depth", depth);
        t.putUUID("owner", owner);
        t.putLong("created", created);
        t.putFloat("hp", hp);
        t.putLong("dmg", lastDamage);
        t.put("base", ids(base)); t.put("below", ids(below)); t.put("adj", ids(adj)); t.put("sup", ids(supporting));
        return t;
    }

    public static Piece load(CompoundTag t) {
        return new Piece(t.getUUID("id"), PieceType.valueOf(t.getString("type")), Tier.valueOf(t.getString("tier")),
                t.getDouble("x"), t.getDouble("y"), t.getDouble("z"), t.getDouble("yaw"), t.getFloat("depth"),
                t.getUUID("owner"), t.getLong("created"), t.getFloat("hp"), t.getLong("dmg"),
                readIds(t, "base"), readIds(t, "below"), readIds(t, "adj"), readIds(t, "sup"));
    }
}
