package com.rustbuild.item;

import com.rustbuild.build.BuildActions;
import com.rustbuild.build.Tier;
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

/** ПКМ - улучшить элемент до выбранного тира, Shift+ПКМ - выбрать тир, ЛКМ - снести элемент. */
public class HammerItem extends Item {
    public static final double REACH = 10.0;

    public HammerItem(Properties props) { super(props); }

    public static Tier getTier(ItemStack s) {
        var tag = ItemData.tag(s);
        int i = tag.contains("tier") ? tag.getInt("tier") : 1;
        Tier[] v = Tier.values();
        return v[Math.floorMod(i, v.length)];
    }

    @Override
    public Component getName(ItemStack stack) {
        return super.getName(stack).copy()
                .append(Component.literal(" ["))
                .append(getTier(stack).displayName())
                .append(Component.literal("]"));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (player.isShiftKeyDown()) {
                Tier[] v = Tier.values();
                Tier next = v[(getTier(stack).ordinal() + 1) % v.length];
                ItemData.setInt(stack, "tier", next.ordinal());
                sp.displayClientMessage(Component.translatable("msg.rustbuild.tier_selected", next.displayName()), true);
            } else {
                BuildActions.upgrade(sp, (ServerLevel) level, REACH, getTier(stack));
                player.getCooldowns().addCooldown(this, 6);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tips, TooltipFlag flag) {
        tips.add(Component.translatable("item.rustbuild.hammer.tip1").withStyle(ChatFormatting.GRAY));
        tips.add(Component.translatable("item.rustbuild.hammer.tip2").withStyle(ChatFormatting.GRAY));
        tips.add(Component.translatable("item.rustbuild.hammer.tip3").withStyle(ChatFormatting.GRAY));
        tips.add(Component.translatable("item.rustbuild.radial.tip").withStyle(ChatFormatting.GOLD));
    }
}
