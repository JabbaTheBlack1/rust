package com.rustbuild.build;

import net.minecraft.network.chat.Component;

public enum PieceType {
    FOUNDATION("foundation", 4),
    FLOOR("floor", 3),
    WALL("wall", 3),
    DOORWAY("doorway", 3),
    WINDOW("window", 3),
    STAIRS("stairs", 4);

    public final String id;
    public final int costMultiplier;

    PieceType(String id, int costMultiplier) {
        this.id = id;
        this.costMultiplier = costMultiplier;
    }

    public boolean isSlab() { return this == FOUNDATION || this == FLOOR; }
    public boolean isWall() { return this == WALL || this == DOORWAY || this == WINDOW; }
    public PieceType next() { return values()[(ordinal() + 1) % values().length]; }
    public Component displayName() { return Component.translatable("piece.rustbuild." + id); }

    public static PieceType byIndex(int i) {
        PieceType[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}
