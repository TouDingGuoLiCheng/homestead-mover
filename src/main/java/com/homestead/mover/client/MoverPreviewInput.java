package com.homestead.mover.client;

import com.homestead.mover.BuildMoverMod;
import com.homestead.mover.item.MoverCaseItem;
import com.homestead.mover.network.MoverImportRequestPacket;
import com.homestead.mover.network.MoverNetwork;
import com.homestead.mover.network.MoverPreviewActionPacket;
import com.homestead.mover.network.MoverPreviewCancelPacket;
import com.homestead.mover.network.MoverPreviewConfirmPacket;
import com.homestead.mover.network.MoverPreviewShiftPacket;
import com.homestead.mover.network.MoverSelectionClickPacket;
import com.homestead.mover.network.MoverSelectionShiftPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Preview controls, laid out the way 施工队 lays them out: left click lands it, right click backs
 * out, the arrow keys turn and nudge, the middle button freezes the spot, V hides the ghost.
 * While only measuring, a left click picks a corner and up/down grows the box instead.
 */
@Mod.EventBusSubscriber(modid = BuildMoverMod.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MoverPreviewInput {
    private MoverPreviewInput() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }
        MoverPreviewClient.follow();
    }

    /** Leaving the world must not leave a ghost or a box outline behind for the next one. */
    @SubscribeEvent
    public static void onLogout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        MoverPreviewClient.clear();
        MoverSelectionClient.clear();
    }

    /**
     * Corner clicks are taken straight off the mouse press. Going through vanilla's left click is
     * what broke the selection: the server cancels the break, the client keeps mining, and every
     * time the case's tag changes the client re-sends the same START, so one click recorded two
     * corners on top of each other (always 1x1x1). Cancelling the press keeps the click single.
     */
    @SubscribeEvent
    public static void onMouse(InputEvent.MouseButton.Pre event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            if (!MoverPreviewClient.isActive()) {
                return;
            }
            MoverPreviewClient.toggleFrozen();
            event.setCanceled(true);
            return;
        }
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT || MoverPreviewClient.isActive()) {
            // the preview's landing click keeps its own path, see onClick below
            return;
        }
        if (MoverCaseItem.heldCase(minecraft.player).isEmpty()) {
            return;
        }
        if (minecraft.hitResult == null || minecraft.hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos clicked = ((BlockHitResult) minecraft.hitResult).getBlockPos();
        MoverNetwork.toServer(new MoverSelectionClickPacket(clicked));
        minecraft.player.swing(InteractionHand.MAIN_HAND);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }
        boolean previewing = MoverPreviewClient.isActive();
        boolean measuring = !previewing && !MoverCaseItem.heldCase(minecraft.player).isEmpty();
        // shift + arrows slide the whole selection across the ground instead of raising it
        // the physical key, not the entity's sneaking flag: that one lags a tick and would let the
        // first shift + arrow slip through as a plain nudge
        boolean shift = minecraft.options.keyShift.isDown();

        switch (event.getKey()) {
            case GLFW.GLFW_KEY_LEFT -> {
                if (previewing) {
                    if (shift) {
                        sendPreviewShift(-1, 0);
                    } else {
                        MoverPreviewClient.rotate(false);
                    }
                } else if (measuring && shift) {
                    sendSelectionShift(-1, 0);
                }
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (previewing) {
                    if (shift) {
                        sendPreviewShift(1, 0);
                    } else {
                        MoverPreviewClient.rotate(true);
                    }
                } else if (measuring && shift) {
                    sendSelectionShift(1, 0);
                }
            }
            case GLFW.GLFW_KEY_UP -> {
                if (previewing) {
                    if (shift) {
                        sendPreviewShift(0, -1);
                    } else {
                        MoverPreviewClient.nudge(1);
                    }
                } else if (measuring) {
                    if (shift) {
                        sendSelectionShift(0, -1);
                    } else {
                        sendSelectionHeight(1);
                    }
                }
            }
            case GLFW.GLFW_KEY_DOWN -> {
                if (previewing) {
                    if (shift) {
                        sendPreviewShift(0, 1);
                    } else {
                        MoverPreviewClient.nudge(-1);
                    }
                } else if (measuring) {
                    if (shift) {
                        sendSelectionShift(0, 1);
                    } else {
                        sendSelectionHeight(-1);
                    }
                }
            }
            case GLFW.GLFW_KEY_I -> {
                // any case in hand, even one still carrying a (possibly missing) blueprint
                if (!MoverCaseItem.heldAnyCase(minecraft.player).isEmpty()) {
                    MoverNetwork.toServer(new MoverImportRequestPacket());
                }
            }
            case GLFW.GLFW_KEY_V -> {
                if (previewing) {
                    MoverPreviewClient.toggleVisible();
                }
            }
            case GLFW.GLFW_KEY_R -> {
                if (previewing) {
                    // R is also vanilla's hand swap: swallow it so the case cannot leave the slot
                    event.setCanceled(true);
                    MoverPreviewClient.rotate(!minecraft.player.isShiftKeyDown());
                }
            }
            default -> {
            }
        }
    }

    /** One block along the ground per key press; up/down walk Z, left/right walk X. */
    private static void sendSelectionShift(int dx, int dz) {
        MoverNetwork.toServer(new MoverSelectionShiftPacket(dx, dz));
    }

    /** Shift + arrows: slide the whole preview one block along the ground (it freezes as it moves). */
    private static void sendPreviewShift(int dx, int dz) {
        MoverPreviewClient.shift(dx, dz);
        // hand the server the ghost's real spot, or it slides whatever it last heard about
        MoverNetwork.toServer(new MoverPreviewShiftPacket(MoverPreviewClient.baseOrigin(),
                MoverPreviewClient.yOffset(), dx, dz));
    }

    private static void sendSelectionHeight(int delta) {
        MoverNetwork.toServer(new MoverPreviewActionPacket(delta > 0
                ? MoverPreviewActionPacket.Action.SELECTION_TALLER
                : MoverPreviewActionPacket.Action.SELECTION_SHORTER));
    }

    /** Left click lands the preview, right click drops it — exactly like a site pick. */
    @SubscribeEvent
    public static void onClick(InputEvent.InteractionKeyMappingTriggered event) {
        if (!MoverPreviewClient.isActive()) {
            return;
        }
        if (event.isAttack()) {
            MoverNetwork.toServer(new MoverPreviewConfirmPacket(MoverPreviewClient.baseOrigin(),
                    MoverPreviewClient.yaw(), MoverPreviewClient.yOffset()));
            event.setCanceled(true);
            event.setSwingHand(false);
        } else if (event.isUseItem()) {
            MoverNetwork.toServer(new MoverPreviewCancelPacket());
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }
}
