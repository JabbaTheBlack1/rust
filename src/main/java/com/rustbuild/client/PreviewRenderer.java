package com.rustbuild.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rustbuild.block.BuildingBlock;
import com.rustbuild.build.BlockEntry;
import com.rustbuild.build.Placement;
import com.rustbuild.build.Resolver;
import com.rustbuild.item.BuildingPlanItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/** Рисует "призрак" элемента: зелёный - можно ставить, красный - нельзя. */
public final class PreviewRenderer {
    private PreviewRenderer() {}

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        var stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof BuildingPlanItem)) return;

        Placement p = Resolver.resolve(mc.level, player, BuildingPlanItem.getPiece(stack));
        if (p == null) return;

        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(RenderType.lines());

        float r = p.valid() ? 0.2f : 1.0f;
        float g = p.valid() ? 1.0f : 0.15f;
        float b = p.valid() ? 0.3f : 0.15f;

        for (BlockEntry e : p.piece().entries()) {
            var st = e.state();
            if (st.getBlock() instanceof BuildingBlock && st.getValue(BuildingBlock.KIND) == BuildingBlock.Kind.FILL) continue;
            AABB bb = new AABB(e.pos()).move(-cam.x, -cam.y, -cam.z).inflate(0.002);
            LevelRenderer.renderLineBox(ps, vc, bb, r, g, b, 0.9f);
        }
        buf.endBatch(RenderType.lines());
    }
}
