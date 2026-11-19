package com.homestead.mover.client;

import com.homestead.mover.capture.Bounds;
import com.homestead.mover.item.MoverCaseItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;

/**
 * The measured box, mirrored on the client so the player can see what they are about to take.
 * Purely a mirror: the server decides, this only draws — and it only draws while the case that
 * owns the selection is in the player's hand, so putting the case away leaves no leftovers.
 */
public final class MoverSelectionClient {
    /** Picked corners get their own colour, so the two clicks stay tellable from the finished box. */
    private static final float CORNER_RED = 0.32F;
    private static final float CORNER_GREEN = 0.95F;
    private static final float CORNER_BLUE = 0.45F;
    private static final float CORNER_ALPHA = 0.95F;

    private static int corners;
    private static BlockPos first;
    private static BlockPos second;
    private static Bounds bounds;

    private MoverSelectionClient() {
    }

    public static void apply(int cornerCount, BlockPos firstCorner, BlockPos secondCorner, Bounds box) {
        corners = cornerCount;
        first = firstCorner;
        second = secondCorner;
        bounds = box;
    }

    public static void clear() {
        corners = 0;
        first = null;
        second = null;
        bounds = null;
    }

    /** A closed box is on screen. */
    public static boolean isActive() {
        return bounds != null;
    }

    /** Something is worth drawing: the closed box, or just a single picked corner. */
    public static boolean drawable() {
        return bounds != null || first != null;
    }

    /** 0, 1 or 2: how many corners the server says are picked. */
    public static int cornerCount() {
        return corners;
    }

    /** The box and the crosshair text belong to the case, so both go away when it is put away. */
    public static boolean shownFor(Minecraft minecraft) {
        return minecraft.player != null && !MoverCaseItem.heldCase(minecraft.player).isEmpty();
    }

    public static int volume() {
        return bounds == null ? 0 : bounds.volume();
    }

    /** "3x3x5（45 格）" */
    public static String summary() {
        return bounds == null
                ? ""
                : bounds.sizeX() + "x" + bounds.sizeY() + "x" + bounds.sizeZ() + "（" + bounds.volume() + " 格）";
    }

    /** The closed box in cold blue, plus one green cube on each corner that was actually clicked. */
    public static void render(PoseStack pose, VertexConsumer lines) {
        if (bounds != null) {
            LevelRenderer.renderLineBox(pose, lines, bounds.minX(), bounds.minY(), bounds.minZ(),
                    bounds.minX() + bounds.sizeX(), bounds.minY() + bounds.sizeY(), bounds.minZ() + bounds.sizeZ(),
                    0.45F, 0.72F, 0.95F, 0.9F);
        }
        renderCorner(pose, lines, first);
        renderCorner(pose, lines, second);
    }

    private static void renderCorner(PoseStack pose, VertexConsumer lines, BlockPos corner) {
        if (corner == null) {
            return;
        }
        LevelRenderer.renderLineBox(pose, lines, corner.getX(), corner.getY(), corner.getZ(),
                corner.getX() + 1.0D, corner.getY() + 1.0D, corner.getZ() + 1.0D,
                CORNER_RED, CORNER_GREEN, CORNER_BLUE, CORNER_ALPHA);
    }
}
