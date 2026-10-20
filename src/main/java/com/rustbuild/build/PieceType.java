package com.rustbuild.build;

import net.minecraft.network.chat.Component;

public enum PieceType {
    FOUNDATION("foundation", 4, 1.5f, Family.FOUNDATION, false),
    TRIANGLE_FOUNDATION("triangle_foundation", 2, 1.5f, Family.FOUNDATION, true),
    FOUNDATION_STEPS("foundation_steps", 1, 0.6f, Family.STEPS, false),
    FLOOR("floor", 3, 1.0f, Family.FLOOR, false),
    TRIANGLE_FLOOR("triangle_floor", 2, 1.0f, Family.FLOOR, true),
    FLOOR_FRAME("floor_frame", 2, 0.9f, Family.FLOOR, false),
    TRIANGLE_FLOOR_FRAME("triangle_floor_frame", 2, 0.9f, Family.FLOOR, true),
    WALL("wall", 3, 1.0f, Family.WALL, false),
    DOORWAY("doorway", 3, 0.6f, Family.WALL, false),
    WINDOW("window", 3, 0.6f, Family.WALL, false),
    LOW_WALL("low_wall", 2, 0.5f, Family.WALL, false),
    ROOF("roof", 3, 0.8f, Family.ROOF, false),
    TRIANGLE_ROOF("triangle_roof", 2, 0.8f, Family.ROOF, true),
    STAIRS("stairs", 4, 0.8f, Family.STAIRS, false),
    L_STAIRS("l_stairs", 4, 0.8f, Family.STAIRS, false),
    U_STAIRS("u_stairs", 4, 0.8f, Family.STAIRS, false),
    SPIRAL_STAIRS("spiral_stairs", 4, 0.8f, Family.STAIRS, false);

    public enum Family { FOUNDATION, FLOOR, ROOF, WALL, STAIRS, STEPS }

    public final String id;
    public final int costMultiplier;
    /** Множитель прочности относительно базовой прочности тира. */
    public final float hpMult;
    public final Family family;
    private final boolean triangle;

    PieceType(String id, int costMultiplier, float hpMult, Family family, boolean triangle) {
        this.id = id;
        this.costMultiplier = costMultiplier;
        this.hpMult = hpMult;
        this.family = family;
        this.triangle = triangle;
    }

    public boolean isTriangle() { return triangle; }
    /** Плита: фундамент или пол. */
    public boolean isSlab() { return family == Family.FOUNDATION || family == Family.FLOOR; }
    /** Всё, что лежит горизонтальной плоскостью и опирается на стены/соседей. */
    public boolean isSlabLike() { return isSlab() || family == Family.ROOF; }
    /** Корень устойчивости. */
    public boolean isRoot() { return family == Family.FOUNDATION; }
    public boolean isWall() { return family == Family.WALL; }
    public boolean isLowWall() { return this == LOW_WALL; }
    public boolean isStairs() { return family == Family.STAIRS; }
    public boolean isFrame() { return this == FLOOR_FRAME || this == TRIANGLE_FLOOR_FRAME; }

    public Component displayName() { return Component.translatable("piece.rustbuild." + id); }

    public PieceType next() { return values()[(ordinal() + 1) % values().length]; }

    public static PieceType byIndex(int i) {
        PieceType[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}
