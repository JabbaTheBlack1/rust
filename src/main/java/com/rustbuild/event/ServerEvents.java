package com.rustbuild.event;

import com.rustbuild.block.BuildingBlock;
import com.rustbuild.build.BuildActions;
import com.rustbuild.build.BuildingData;
import com.rustbuild.build.Piece;
import com.rustbuild.item.HammerItem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class ServerEvents {
    private ServerEvents() {}

    /** Постройки нельзя ломать руками - только молотком (в творческом режиме можно). */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        BlockState st = e.getState();
        boolean ours = st.getBlock() instanceof BuildingBlock;
        Piece found = null;
        if (!ours && st.getBlock() instanceof StairBlock) {
            found = BuildingData.get(level).findAt(e.getPos(), st);
            ours = found != null;
        }
        if (!ours) return;

        Player p = e.getPlayer();
        if (p.isCreative()) {
            BuildingData data = BuildingData.get(level);
            Piece piece = found != null ? found : data.findAt(e.getPos(), st);
            if (piece != null) data.remove(piece);
            return;
        }
        e.setCanceled(true);
        p.displayClientMessage(Component.translatable("msg.rustbuild.use_hammer"), true);
    }

    /** ЛКМ молотком = снос элемента (обычное ломание блока отключено). */
    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock e) {
        if (!(e.getItemStack().getItem() instanceof HammerItem)) return;
        e.setCanceled(true);
        if (e.getLevel().isClientSide()) return;
        if (e.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        if (e.getEntity() instanceof ServerPlayer sp && e.getLevel() instanceof ServerLevel level) {
            BuildActions.demolish(sp, level, e.getPos());
        }
    }
}
