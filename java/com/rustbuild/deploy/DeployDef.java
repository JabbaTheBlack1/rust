package com.rustbuild.deploy;

import com.rustbuild.build.Solid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/** Тела свободных предметов. Локальные координаты: начало в центре низа, лицевая сторона смотрит к +Z. */
public final class DeployDef {
    private DeployDef() {}

    public static List<Solid> solids(DeployKind kind, ItemStack stack) {
        List<Solid> out = new ArrayList<>();
        switch (kind) {
            case TOOL_CUPBOARD -> out.add(Solid.box(-0.45, 0, -0.30, 0.45, 1.7, 0.30));
            case STORAGE_BOX -> out.add(Solid.box(-0.52, 0, -0.37, 0.52, 0.8, 0.37));
            case WORKBENCH -> out.add(Solid.box(-0.6, 0, -0.35, 0.6, 0.9, 0.35));
            case DECOR -> {
                if (stack.getItem() instanceof BlockItem bi) {
                    BlockState st = bi.getBlock().defaultBlockState();
                    VoxelShape shape = st.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                    for (AABB b : shape.toAabbs()) {
                        out.add(Solid.box(b.minX - 0.5, b.minY, b.minZ - 0.5, b.maxX - 0.5, b.maxY, b.maxZ - 0.5));
                    }
                    if (out.isEmpty()) out.add(Solid.box(-0.2, 0, -0.2, 0.2, 0.4, 0.2));
                } else {
                    out.add(Solid.box(-0.25, 0, -0.25, 0.25, 0.1, 0.25));   // предмет лежит на поверхности
                }
            }
        }
        return out;
    }

    /** Предметы без собственной формы не мешают ходить (только блоки и крупные объекты дают коллизию). */
    public static boolean collides(DeployKind kind, ItemStack stack) {
        return kind != DeployKind.DECOR || stack.getItem() instanceof BlockItem;
    }
}
