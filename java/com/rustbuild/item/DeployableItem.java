package com.rustbuild.item;

import com.rustbuild.build.BuildingData;
import com.rustbuild.deploy.DeployActions;
import com.rustbuild.deploy.DeployKind;
import com.rustbuild.deploy.DeployResolver;
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

/** Предмет, который ставится как свободный объект (шкаф, ящик, верстак): в точку прицела, под любым углом (Z / X). */
public class DeployableItem extends Item {
    public final DeployKind kind;

    public DeployableItem(Properties props, DeployKind kind) {
        super(props);
        this.kind = kind;
    }

    public static int getRot(ItemStack s) { return ItemData.getInt(s, "rot", 0); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            BuildingData data = BuildingData.get((ServerLevel) level);
            DeployResolver.Placed pl = DeployResolver.resolve(level, player, kind, ItemStack.EMPTY, getRot(stack), box -> data.inBox(box));
            if (pl == null) {
                sp.displayClientMessage(Component.translatable("msg.rustbuild.aim_floor"), true);
            } else if (!pl.valid()) {
                sp.displayClientMessage(Component.translatable("msg.rustbuild.invalid." + pl.reason()), true);
            } else if (DeployActions.place(sp, (ServerLevel) level, kind, ItemStack.EMPTY, pl) && !player.isCreative()) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tips, TooltipFlag flag) {
        tips.add(Component.translatable("item.rustbuild.deployable.tip1").withStyle(ChatFormatting.GRAY));
        tips.add(Component.translatable("item.rustbuild.deployable.tip2").withStyle(ChatFormatting.GRAY));
    }
}
