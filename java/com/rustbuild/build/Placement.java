package com.rustbuild.build;

import javax.annotation.Nullable;

/** Результат прицеливания: элемент, можно ли его поставить и причина отказа. */
public record Placement(Piece piece, boolean valid, @Nullable String reason) {}
