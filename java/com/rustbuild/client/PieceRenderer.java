package com.rustbuild.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rustbuild.block.BuildingBlock;
import com.rustbuild.block.ModBlocks;
import com.rustbuild.build.PieceType;
import com.rustbuild.entity.PieceEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Рисует элемент постройки JSON-моделью (как блок-модель), повёрнутой на произвольный угол.
 * Модели нарисованы в масштабе 1/1.5 (ограничение размера блок-модели), поэтому здесь они увеличиваются в 1.5 раза.
 */
public class PieceRenderer extends EntityRenderer<PieceEntity> {
    public PieceRenderer(EntityRendererProvider.Context ctx) { super(ctx); }

    @Override
    public ResourceLocation getTextureLocation(PieceEntity e) { return InventoryMenu.BLOCK_ATLAS; }

    @Override
    public boolean shouldRender(PieceEntity e, Frustum frustum, double x, double y, double z) {
        return e.distanceToSqr(x, y, z) < 200.0 * 200.0;
    }

    @Override
    public void render(PieceEntity e, float yaw, float partialTick, PoseStack ps, MultiBufferSource buf, int light) {
        BlockState st = ModBlocks.get(e.tier()).defaultBlockState().setValue(BuildingBlock.PIECE, e.pieceType());

        int packed = Math.max(light, LevelRenderer.getLightColor(e.level(), BlockPos.containing(e.getX(), e.getY() + 1.4, e.getZ())));

        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(e.pieceYaw()));
        if (e.pieceType().family == PieceType.Family.ROOF) ps.translate(0.0, 0.0, 1.5);   // модель крыши сдвинута к центру блока
        float sy = e.pieceType().isRoot() ? e.depth() : 1.5f;     // у фундамента модель растягивается до земли
        if (e.pieceType().family == PieceType.Family.WALL) {
            // соседние стены под разными углами получают чуть разную высоту (3 мм на шаг 30 градусов),
            // иначе верхние грани в общем углу совпадают по глубине и мерцают
            int k = Math.floorMod((int) Math.floor(e.pieceYaw() / 30.0), 12);
            sy = 1.5f * (1.0f + 0.003f * k / 2.75f);
        }
        ps.scale(1.5f, sy, 1.5f);
        ps.translate(-0.5, 0.0, -0.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(st, ps, buf, packed, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }
}
