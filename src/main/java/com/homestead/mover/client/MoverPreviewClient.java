package com.homestead.mover.client;

import com.homestead.mover.placement.PlacementRules;
import com.homestead.mover.placement.PreviewScan;
import com.homestead.mover.placement.SchematicYaw;
import com.homestead.mover.snapshot.BoxGeometry;
import com.homestead.mover.snapshot.CapturedBuilding;
import com.homestead.mover.snapshot.RegionSnapshot;
import com.homestead.mover.snapshot.SnapshotNbt;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Client half of the preview. The blocks arrive once; from then on the ghost follows the player's
 * crosshair locally (only while a preview is actually open), the arrow keys turn and nudge it, and
 * the middle button freezes it. Conflicts are recomputed here so the red cells and the HUD list
 * are instant, but the server still re-checks everything before anything lands.
 */
public final class MoverPreviewClient {
    /** Outline drawing is per cell; a hard cap keeps a ten-thousand-block building from stuttering. */
    public static final int MAX_CELLS = 24_000;
    /** How many blocking blocks the HUD names before it just counts the rest. */
    public static final int MAX_CONFLICT_LINES = 4;
    private static final int MIN_Y_OFFSET = -64;
    private static final int MAX_Y_OFFSET = 320;

    private static boolean active;
    private static boolean visible = true;
    private static boolean frozen;
    private static String blueprintId = "";
    private static RegionSnapshot region;
    private static BlockPos baseOrigin = BlockPos.ZERO;
    private static int yaw;
    private static int yOffset;
    private static int[] cells = new int[0];
    private static boolean[] conflicts = new boolean[0];
    private static boolean truncated;
    private static int conflictCount;
    private static final List<BlockPos> conflictSamples = new ArrayList<>();
    private static final List<String> conflictNames = new ArrayList<>();

    private MoverPreviewClient() {
    }

    public static void begin(String id, CompoundTag blob, BlockPos origin, int previewYaw) {
        blueprintId = id == null ? "" : id;
        region = null;
        try {
            CapturedBuilding building = SnapshotNbt.decode(blob);
            region = building.snapshot().region();
        } catch (IllegalArgumentException broken) {
            region = null;
        }
        baseOrigin = origin;
        yaw = SchematicYaw.normalize(previewYaw);
        yOffset = 0;
        frozen = false;
        visible = true;
        active = region != null;
        rebuild();
    }

    /** The server moved the preview itself (its own rotate/nudge paths). */
    public static void update(BlockPos origin, int previewYaw) {
        if (region == null) {
            return;
        }
        baseOrigin = origin;
        yaw = SchematicYaw.normalize(previewYaw);
        yOffset = 0;
        active = true;
        rebuild();
    }

    public static void clear() {
        active = false;
        frozen = false;
        region = null;
        cells = new int[0];
        conflicts = new boolean[0];
        conflictSamples.clear();
        conflictNames.clear();
        conflictCount = 0;
        truncated = false;
    }

    /**
     * Walk the ghost to whatever block the crosshair is on. Called once per client tick, and only
     * while the player has opened the preview: the case never draws a preview by itself.
     */
    public static void follow() {
        if (!active || frozen || region == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.hitResult == null || minecraft.hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos clicked = ((BlockHitResult) minecraft.hitResult).getBlockPos();
        BlockPos next = PlacementRules.originOnTopOf(clicked);
        if (next.equals(baseOrigin)) {
            return;
        }
        baseOrigin = next;
        rebuild();
    }

    public static void rotate(boolean clockwise) {
        if (!active) {
            return;
        }
        yaw = clockwise ? SchematicYaw.rotateRight(yaw) : SchematicYaw.rotateLeft(yaw);
        rebuild();
    }

    /**
     * Shift + arrows: slide the ghost one block along the ground. The spot is frozen as a side
     * effect, because {@link #follow()} would otherwise drag it back under the crosshair next tick.
     */
    public static void shift(int dx, int dz) {
        if (!active || (dx == 0 && dz == 0)) {
            return;
        }
        frozen = true;
        baseOrigin = baseOrigin.offset(dx, 0, dz);
        rebuild();
    }

    public static void nudge(int delta) {
        if (!active) {
            return;
        }
        yOffset = Math.max(MIN_Y_OFFSET, Math.min(MAX_Y_OFFSET, yOffset + delta));
        rebuild();
    }

    public static void toggleFrozen() {
        frozen = !frozen;
    }

    public static boolean isFrozen() {
        return frozen;
    }

    public static boolean isActive() {
        return active && region != null;
    }

    public static boolean isVisible() {
        return visible;
    }

    public static void setVisible(boolean value) {
        visible = value;
    }

    public static boolean toggleVisible() {
        visible = !visible;
        return visible;
    }

    public static String blueprintId() {
        return blueprintId;
    }

    public static BlockPos baseOrigin() {
        return baseOrigin;
    }

    public static BlockPos origin() {
        return baseOrigin.offset(0, yOffset, 0);
    }

    public static int yaw() {
        return yaw;
    }

    public static int yOffset() {
        return yOffset;
    }

    public static int cellCount() {
        return cells.length;
    }

    static RegionSnapshot region() {
        return region;
    }

    static int[] cells() {
        return cells;
    }

    static boolean[] conflicts() {
        return conflicts;
    }

    public static int conflictCount() {
        return conflictCount;
    }

    public static List<BlockPos> conflictSamples() {
        return conflictSamples;
    }

    public static List<String> conflictNames() {
        return conflictNames;
    }

    public static boolean truncated() {
        return truncated;
    }

    /** Surface cells worth showing, plus which of them would overwrite something. */
    private static void rebuild() {
        conflictSamples.clear();
        conflictNames.clear();
        conflictCount = 0;
        if (region == null) {
            cells = new int[0];
            conflicts = new boolean[0];
            truncated = false;
            return;
        }
        PreviewScan.Result result = PreviewScan.scan(Minecraft.getInstance().level, region, origin(), yaw,
                MAX_CELLS, MAX_CONFLICT_LINES);
        cells = result.cells();
        conflicts = result.conflicts();
        conflictCount = result.conflictCount();
        conflictSamples.addAll(result.sampleConflicts());
        conflictNames.addAll(result.sampleNames());
        truncated = result.truncated();
    }

    /** Draws one outline per recorded cell; conflicts go red, the rest stay cold blue. */
    public static void renderGhost(PoseStack pose, VertexConsumer consumer) {
        if (!isActive()) {
            return;
        }
        BlockPos origin = origin();
        for (int i = 0; i < cells.length; i++) {
            int[] offset = BoxGeometry.offsetOf(cells[i], region.sizeX(), region.sizeZ());
            BlockPos pos = PreviewScan.worldOf(region, origin, yaw, offset[0], offset[1], offset[2]);
            boolean conflict = conflicts[i];
            LevelRenderer.renderLineBox(pose, consumer, pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1.0D, pos.getY() + 1.0D, pos.getZ() + 1.0D,
                    conflict ? 1.0F : 0.35F, conflict ? 0.22F : 0.78F, conflict ? 0.22F : 1.0F,
                    conflict ? 1.0F : 0.75F);
        }
    }
}
