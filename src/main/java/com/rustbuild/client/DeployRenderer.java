package com.rustbuild.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rustbuild.block.DeployModelBlock;
import com.rustbuild.block.ModBlocks;
import com.rustbuild.deploy.DeployKind;
import com.rustbuild.deploy.DeployableEntity;
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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/** Рисует свободные предметы: свои модели (шкаф, ящик, верстак) либо любой блок/предмет под произвольным углом. */
public class DeployRenderer extends EntityRenderer<DeployableEntity> {
    public DeployRenderer(EntityRendererProvider.Context ctx) { super(ctx); }

    @Override
    public ResourceLocation getTextureLocation(DeployableEntity e) { return InventoryMenu.BLOCK_ATLAS; }

    @Override
    public boolean shouldRender(DeployableEntity e, Frustum frustum, double x, double y, double z) {
        return e.distanceToSqr(x, y, z) < 128.0 * 128.0;
    }

    @Override
    public void render(DeployableEntity e, float yaw, float partialTick, PoseStack ps, MultiBufferSource buf, int light) {
        int packed = Math.max(light, LevelRenderer.getLightColor(e.level(), BlockPos.containing(e.getX(), e.getY() + 0.6, e.getZ())));
        DeployKind kind = e.kind();
        var renderer = Minecraft.getInstance().getBlockRenderer();

        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(e.objectYaw()));

        if (kind != DeployKind.DECOR) {
            BlockState st = ModBlocks.DEPLOY_MODEL.get().defaultBlockState().setValue(DeployModelBlock.KIND, kind);
            ps.scale(1.5f, 1.5f, 1.5f);
            ps.translate(-0.5, 0.0, -0.5);
            renderer.renderSingleBlock(st, ps, buf, packed, OverlayTexture.NO_OVERLAY);
        } else {
            ItemStack stack = e.stack();
            BlockState st = stack.getItem() instanceof BlockItem bi ? bi.getBlock().defaultBlockState() : null;
            if (st != null && st.getRenderShape() == RenderShape.MODEL) {
                ps.translate(-0.5, 0.0, -0.5);
                renderer.renderSingleBlock(st, ps, buf, packed, OverlayTexture.NO_OVERLAY);
            } else if (!stack.isEmpty()) {
                ps.translate(0.0, 0.2, 0.0);
                ps.mulPose(Axis.XP.rotationDegrees(90.0f));   // предмет лежит на поверхности
                ps.scale(0.9f, 0.9f, 0.9f);
                Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, packed,
                        OverlayTexture.NO_OVERLAY, ps, buf, e.level(), e.getId());
            }
        }
        ps.popPose();
    }
}
