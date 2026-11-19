package com.homestead.mover.client;

import com.homestead.mover.BuildMoverMod;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.phys.Vec3;

/** Draws the measured box and the ghost after the world is translucent: no chunks are touched. */
@Mod.EventBusSubscriber(modid = BuildMoverMod.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MoverGhostRenderer {
    private MoverGhostRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        boolean box = MoverSelectionClient.drawable() && MoverSelectionClient.shownFor(minecraft);
        boolean ghost = MoverPreviewClient.isActive() && MoverPreviewClient.isVisible();
        if (!box && !ghost) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        RenderSystem.lineWidth(2.0F);
        RenderSystem.disableCull();
        try {
            if (box) {
                MoverSelectionClient.render(pose, consumer);
            }
            if (ghost) {
                drawGhost(minecraft, pose, consumer);
            } else {
                MoverGhostMesh.close();
            }
        } finally {
            RenderSystem.enableCull();
            RenderSystem.lineWidth(1.0F);
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    /**
     * Real block models while they are affordable; the cheap outline for anything huge or once a
     * bake has ever thrown. A broken preview must never cost the player their frame rate.
     */
    private static void drawGhost(Minecraft minecraft, PoseStack pose, VertexConsumer lines) {
        if (!MoverGhostMesh.broken() && MoverGhostMesh.usable(MoverPreviewClient.cellCount())) {
            try {
                MoverGhostMesh.build(minecraft, MoverPreviewClient.region(), MoverPreviewClient.yaw(),
                        MoverPreviewClient.cells(), MoverPreviewClient.conflicts());
                MoverGhostMesh.draw(pose, MoverPreviewClient.origin());
                return;
            } catch (RuntimeException | LinkageError failure) {
                MoverGhostMesh.markBroken(failure instanceof RuntimeException runtime
                        ? runtime : new IllegalStateException(failure));
            }
        }
        MoverPreviewClient.renderGhost(pose, lines);
    }
}
