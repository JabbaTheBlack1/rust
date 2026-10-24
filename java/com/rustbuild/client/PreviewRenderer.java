package com.rustbuild.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.rustbuild.build.Piece;
import com.rustbuild.build.PieceDef;
import com.rustbuild.build.PieceType;
import com.rustbuild.build.Placement;
import com.rustbuild.build.Resolver;
import com.rustbuild.entity.PieceEntity;
import com.rustbuild.deploy.DeployResolver;
import com.rustbuild.item.BuildingPlanItem;
import com.rustbuild.item.DeployableItem;
import com.rustbuild.build.Solid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import java.util.List;
import java.util.function.Function;

/** Контур элемента под произвольным углом: зелёный - можно ставить, красный - нельзя, оранжевый - проёмы. */
public final class PreviewRenderer {
    private PreviewRenderer() {}

    private static void drawDeployable(RenderLevelStageEvent event, Minecraft mc, LocalPlayer player, DeployableItem di,
                                       net.minecraft.world.item.ItemStack stack) {
        Function<AABB, List<Piece>> provider = box ->
                mc.level.getEntitiesOfClass(PieceEntity.class, box).stream().map(PieceEntity::toView).toList();
        DeployResolver.Placed p = DeployResolver.resolve(mc.level, player, di.kind, net.minecraft.world.item.ItemStack.EMPTY,
                DeployableItem.getRot(stack), provider);
        if (p == null) return;
        PoseStack ps = new PoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(RenderType.lines());
        float r = p.valid() ? 0.25f : 1.0f, g = p.valid() ? 1.0f : 0.2f, b = p.valid() ? 0.35f : 0.2f;
        ps.pushPose();
        ps.translate(p.xf().x() - cam.x, p.xf().y() - cam.y, p.xf().z() - cam.z);
        ps.mulPose(Axis.YP.rotationDegrees((float) p.xf().yaw()));
        for (Solid s : p.solids()) LevelRenderer.renderLineBox(ps, vc, s.bounds().inflate(0.003), r, g, b, 1.0f);
        ps.popPose();
        buf.endBatch(RenderType.lines());
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        var stack = player.getMainHandItem();
        if (stack.getItem() instanceof DeployableItem di) {
            drawDeployable(event, mc, player, di, stack);
            return;
        }
        if (!(stack.getItem() instanceof BuildingPlanItem)) return;

        PieceType type = BuildingPlanItem.getPiece(stack);
        Function<AABB, List<Piece>> provider = box ->
                mc.level.getEntitiesOfClass(PieceEntity.class, box).stream().map(PieceEntity::toView).toList();
        Placement p = Resolver.resolve(mc.level, player, type, BuildingPlanItem.getRot(stack), provider);
        if (p == null) return;
        Piece piece = p.piece();

        // Поворот камеры уже применён в матрице рендера, поэтому берём чистый PoseStack.
        PoseStack ps = new PoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(RenderType.lines());

        float r = p.valid() ? 0.25f : 1.0f;
        float g = p.valid() ? 1.0f : 0.2f;
        float b = p.valid() ? 0.35f : 0.2f;

        ps.pushPose();
        ps.translate(piece.x() - cam.x, piece.y() - cam.y, piece.z() - cam.z);
        ps.mulPose(Axis.YP.rotationDegrees((float) piece.yaw()));
        for (AABB box : PieceDef.previewBoxes(type, piece.depth())) {
            LevelRenderer.renderLineBox(ps, vc, box.inflate(0.003), r, g, b, 1.0f);
        }
        for (AABB box : PieceDef.previewCutouts(type)) {
            LevelRenderer.renderLineBox(ps, vc, box.inflate(0.003), 1.0f, 0.65f, 0.1f, 1.0f);
        }
        ps.popPose();

        buf.endBatch(RenderType.lines());
    }
}
