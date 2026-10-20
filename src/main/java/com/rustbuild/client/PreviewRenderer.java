package com.rustbuild.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rustbuild.build.Piece;
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

/** Контур элемента: зелёный - можно ставить, красный - нельзя, оранжевый - проёмы. */
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
        Piece piece = p.piece();

        // Поворот камеры уже применён к матрице рендера, поэтому берём ЧИСТЫЙ PoseStack
        // и сдвигаем координаты относительно камеры.
        PoseStack ps = new PoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(RenderType.lines());

        float r = p.valid() ? 0.25f : 1.0f;
        float g = p.valid() ? 1.0f : 0.2f;
        float b = p.valid() ? 0.35f : 0.2f;
        for (AABB box : piece.previewBoxes()) draw(ps, vc, box, cam, r, g, b);
        for (AABB box : piece.previewCutouts()) draw(ps, vc, box, cam, 1.0f, 0.65f, 0.1f);

        buf.endBatch(RenderType.lines());
    }

    private static void draw(PoseStack ps, VertexConsumer vc, AABB box, Vec3 cam, float r, float g, float b) {
        AABB bb = box.move(-cam.x, -cam.y, -cam.z).inflate(0.003);
        LevelRenderer.renderLineBox(ps, vc, bb, r, g, b, 1.0f);
    }
}
