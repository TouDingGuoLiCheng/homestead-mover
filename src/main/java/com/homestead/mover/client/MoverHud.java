package com.homestead.mover.client;

import com.homestead.mover.BuildMoverMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * All the text this mod shows under the crosshair: what the arrow keys do, whether the spot is
 * frozen, and — most importantly — what is standing in the way, block by block.
 */
@Mod.EventBusSubscriber(modid = BuildMoverMod.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MoverHud {
    private MoverHud() {
    }

    @SubscribeEvent
    public static void onHud(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }
        List<Component> lines = lines(minecraft);
        if (lines.isEmpty()) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = minecraft.font;
        int centre = event.getWindow().getGuiScaledWidth() / 2;
        int y = event.getWindow().getGuiScaledHeight() / 2 + 16;
        for (Component line : lines) {
            graphics.drawString(font, line, centre - font.width(line) / 2, y, 0xFFF5E6D3, true);
            y += 11;
        }
    }

    private static List<Component> lines(Minecraft minecraft) {
        List<Component> lines = new ArrayList<>();
        if (MoverPreviewClient.isActive()) {
            if (MoverPreviewClient.truncated()) {
                lines.add(Component.translatable("homestead_mover.hud.preview_capped"));
            }
            if (MoverPreviewClient.cellCount() > 0 && !MoverGhostMesh.usable(MoverPreviewClient.cellCount())) {
                lines.add(Component.translatable("homestead_mover.hud.preview_outline",
                        MoverPreviewClient.cellCount()));
            }
            if (!MoverPreviewClient.isVisible()) {
                lines.add(Component.translatable("homestead_mover.hud.hidden"));
            }
            if (MoverPreviewClient.isFrozen()) {
                lines.add(Component.translatable("homestead_mover.hud.frozen"));
            }
            int conflicts = MoverPreviewClient.conflictCount();
            if (conflicts > 0) {
                lines.add(Component.translatable("homestead_mover.hud.conflict_head", conflicts));
                List<BlockPos> samples = MoverPreviewClient.conflictSamples();
                List<String> names = MoverPreviewClient.conflictNames();
                for (int i = 0; i < samples.size(); i++) {
                    BlockPos pos = samples.get(i);
                    lines.add(Component.translatable("homestead_mover.hud.conflict_line",
                            names.get(i), pos.getX(), pos.getY(), pos.getZ()));
                }
                if (conflicts > samples.size()) {
                    lines.add(Component.translatable("homestead_mover.hud.conflict_more", conflicts - samples.size()));
                }
            }
            lines.add(Component.translatable("homestead_mover.hud.preview_keys"));
            return lines;
        }
        // the crosshair text belongs to the case: nothing at all once it has been put away
        if (!MoverSelectionClient.shownFor(minecraft)) {
            return lines;
        }
        int corners = MoverSelectionClient.cornerCount();
        if (corners >= 2) {
            lines.add(Component.translatable("homestead_mover.hud.selection", MoverSelectionClient.summary()));
        } else if (corners == 1) {
            lines.add(Component.translatable("homestead_mover.hud.selection_one"));
        }
        lines.add(Component.translatable(corners == 0
                ? "homestead_mover.hud.measure_keys"
                : "homestead_mover.hud.selection_keys"));
        return lines;
    }
}
