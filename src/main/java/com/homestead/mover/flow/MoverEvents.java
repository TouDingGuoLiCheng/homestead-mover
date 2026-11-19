package com.homestead.mover.flow;

import com.homestead.mover.BuildMoverMod;
import com.homestead.mover.item.MoverCaseItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BuildMoverMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MoverEvents {
    private MoverEvents() {
    }

    @SubscribeEvent
    public static void onLoggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MoverService.forgetPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            MoverService.tick(level);
        }
    }

    /**
     * The empty case never breaks blocks. The corner itself arrives as the client's own click
     * packet: letting vanilla's left click through keeps the client mining, and once the case's
     * tag changes it re-sends START_DESTROY_BLOCK every tick — two corners per click, a permanent
     * 1x1x1 area.
     */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (MoverService.placementOf(player) != null) {
            // preview open: left click is the landing click, never a new corner and never a break
            event.setCanceled(true);
            return;
        }
        ItemStack stack = event.getItemStack();
        if (stack.getItem() instanceof MoverCaseItem && !MoverCaseItem.isBound(stack)) {
            event.setCanceled(true);
        }
    }

    /**
     * Right click opens the preview; with a preview open it drops it (the same thing the client
     * does through the cancel packet). Sneak + right click still means "the other thing":
     * collect a fresh building or put a filled one back.
     */
    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof MoverCaseItem)) {
            return;
        }
        if (MoverService.placementOf(player) != null) {
            event.setCanceled(true);
            MoverService.cancelPlacement(player);
            return;
        }
        if (player.isShiftKeyDown()) {
            event.setCanceled(true);
            if (MoverCaseItem.isBound(stack)) {
                MoverService.restore(player, stack);
            } else {
                MoverService.requestCapture(player, stack);
            }
            return;
        }
        event.setCanceled(true);
        MoverService.usePlacement(player, stack, event.getPos());
    }
}
