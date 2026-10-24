package com.rustbuild.item;

import com.rustbuild.build.BuildActions;
import com.rustbuild.build.BuildingData;
import com.rustbuild.build.PieceType;
import com.rustbuild.build.Placement;
import com.rustbuild.build.Resolver;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** ПКМ - поставить элемент, Shift+ПКМ - выбрать следующий элемент. */
public class BuildingPlanItem extends Item {
    public BuildingPlanItem(Properties props) { super(props); }

    public static PieceType getPiece(ItemStack s) {
        return PieceType.byIndex(ItemData.tag(s).getInt("piece"));
    }

    /** Дополнительный угол поворота первого фундамента (градусы). */
    public static int getRot(ItemStack s) { return ItemData.getInt(s, "rot", 0); }

    @Override
    public Component getName(ItemStack stack) {
        return super.getName(stack).copy()
                .append(Component.literal(" ["))
                .append(getPiece(stack).displayName())
                .append(Component.literal("]"));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (player.isShiftKeyDown()) {
                PieceType next = getPiece(stack).next();
                ItemData.setInt(stack, "piece", next.ordinal());
                sp.displayClientMessage(Component.translatable("msg.rustbuild.piece_selected", next.displayName()), true);
            } else {
                BuildingData data = BuildingData.get((ServerLevel) level);
                Placement pl = Resolver.resolve(level, player, getPiece(stack), getRot(stack), box -> data.inBox(box));
                if (pl == null) {
                    sp.displayClientMessage(Component.translatable("msg.rustbuild.aim"), true);
                } else if (!pl.valid()) {
                    sp.displayClientMessage(Component.translatable("msg.rustbuild.invalid." + pl.reason()), true);
                } else {
                    BuildActions.place(sp, (ServerLevel) level, pl);
                    player.getCooldowns().addCooldown(this, 4);
                }
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tips, TooltipFlag flag) {
        tips.add(Component.translatable("item.rustbuild.building_plan.tip1").withStyle(ChatFormatting.GRAY));
        tips.add(Component.translatable("item.rustbuild.building_plan.tip2").withStyle(ChatFormatting.GRAY));
        tips.add(Component.translatable("item.rustbuild.radial.tip").withStyle(ChatFormatting.GOLD));
    }
}
