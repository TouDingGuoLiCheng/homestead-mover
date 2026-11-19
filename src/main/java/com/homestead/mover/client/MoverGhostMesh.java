package com.homestead.mover.client;

import com.homestead.mover.placement.BlockStateResolve;
import com.homestead.mover.placement.SchematicYaw;
import com.homestead.mover.snapshot.BoxGeometry;
import com.homestead.mover.snapshot.RegionSnapshot;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The see-through copy of a building: every cell is baked with its real block model into a GPU
 * buffer, then drawn with the translucent render type. Conflicts are coloured while baking
 * (a vertex tint, exactly the trick 施工队 uses), so moving the ghost only re-bakes when the
 * blocking pattern actually changes — never once per frame.
 *
 * <p>Anything that throws disables the mesh for the session and the caller falls back to the
 * outline, so a broken model can never take the frame (or the game) down with it.
 */
public final class MoverGhostMesh {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Above this many surface cells the bake is not worth the one-off hitch: the outline takes over. */
    public static final int CELL_LIMIT = 40_000;
    /** A bake slower than this is a hitch the player feels; large previews then keep the outline. */
    private static final long SLOW_BAKE_NANOS = 400_000_000L;
    private static final int LARGE_PREVIEW_CELLS = 4_000;
    /** Flat blocks read better as a ghost when they are a little dimmer than the real thing. */
    private static final float ALPHA = 0.55F;

    private static final Map<RenderType, Layer> LAYERS = new LinkedHashMap<>();
    private static long builtKey = Long.MIN_VALUE;
    private static boolean slowBake;
    private static boolean broken;

    private MoverGhostMesh() {
    }

    /** Whether this preview should be drawn as baked blocks at all. */
    public static boolean usable(int cellCount) {
        if (broken || cellCount <= 0 || cellCount > CELL_LIMIT) {
            return false;
        }
        return !(slowBake && cellCount > LARGE_PREVIEW_CELLS);
    }

    public static boolean broken() {
        return broken;
    }

    /** Something threw: never try the mesh again this session. */
    public static void markBroken(RuntimeException cause) {
        LOGGER.warn("Translucent preview mesh failed, falling back to the outline", cause);
        broken = true;
        close();
    }

    public static void build(Minecraft minecraft, RegionSnapshot region, int yaw, int[] cells, boolean[] conflicts) {
        long key = keyOf(region, yaw, cells, conflicts);
        if (key == builtKey && !LAYERS.isEmpty()) {
            return;
        }
        close();
        long start = System.nanoTime();
        Capture capture = new Capture();
        BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
        PoseStack pose = new PoseStack();
        int sizeX = region.sizeX();
        int sizeZ = region.sizeZ();
        try {
            for (int i = 0; i < cells.length; i++) {
                int[] offset = BoxGeometry.offsetOf(cells[i], sizeX, sizeZ);
                BlockState planned = BlockStateResolve.resolveRotated(
                        region.blockAt(offset[0], offset[1], offset[2]), yaw);
                if (planned.isAir()) {
                    continue;
                }
                // baked in rotated local space: the draw call only translates by the world origin
                int localX = SchematicYaw.offsetX(offset[0], offset[2], sizeX, sizeZ, yaw);
                int localZ = SchematicYaw.offsetZ(offset[0], offset[2], sizeX, sizeZ, yaw);
                pose.pushPose();
                pose.translate(localX, offset[1], localZ);
                capture.setConflict(conflicts[i]);
                try {
                    dispatcher.renderSingleBlock(planned, pose, capture, 0xF000F0,
                            OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.translucent());
                } catch (Exception exotic) {
                    // a model that will not bake is not worth losing the preview over
                }
                pose.popPose();
            }
            capture.finish();
        } catch (RuntimeException | LinkageError failure) {
            capture.discard();
            close();
            if (failure instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(failure);
        }
        long elapsed = System.nanoTime() - start;
        if (elapsed > SLOW_BAKE_NANOS) {
            slowBake = true;
            LOGGER.info("Preview bake took {} ms; large previews will use the outline from now on",
                    String.format("%.0f", elapsed / 1_000_000.0));
        }
        builtKey = key;
    }

    /** Camera-relative pose in, world-locked blocks out — the same contract as the outline path. */
    public static void draw(PoseStack pose, BlockPos origin) {
        if (LAYERS.isEmpty()) {
            return;
        }
        RenderSystem.assertOnRenderThread();
        pose.pushPose();
        pose.translate(origin.getX(), origin.getY(), origin.getZ());
        Matrix4f modelView = new Matrix4f(pose.last().pose());
        pose.popPose();
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        try {
            for (Layer layer : LAYERS.values()) {
                layer.type.setupRenderState();
                ShaderInstance shader = RenderSystem.getShader();
                if (shader != null) {
                    layer.gpu.bind();
                    layer.gpu.drawWithShader(modelView, projection, shader);
                    VertexBuffer.unbind();
                }
                layer.type.clearRenderState();
            }
        } finally {
            VertexBuffer.unbind();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
    }

    /** Drops the baked buffers; called when the preview closes, on logout and on failure. */
    public static void close() {
        if (!RenderSystem.isOnRenderThread()) {
            return;
        }
        for (Layer layer : LAYERS.values()) {
            layer.gpu.close();
        }
        LAYERS.clear();
        builtKey = Long.MIN_VALUE;
    }

    /** Everything that changes what the baked colours must look like. */
    private static long keyOf(RegionSnapshot region, int yaw, int[] cells, boolean[] conflicts) {
        long hash = 1_125_899_906_842_597L;
        hash = hash * 31 + System.identityHashCode(region);
        hash = hash * 31 + yaw;
        hash = hash * 31 + cells.length;
        for (int cell : cells) {
            hash = hash * 31 + cell;
        }
        for (boolean conflict : conflicts) {
            hash = hash * 31 + (conflict ? 1 : 0);
        }
        return hash;
    }

    private static final class Layer {
        private final RenderType type;
        private final VertexBuffer gpu;

        private Layer(RenderType type, VertexBuffer gpu) {
            this.type = type;
            this.gpu = gpu;
        }
    }

    /** Collects one private buffer per render type, so the shared level buffer is never flushed. */
    private static final class Capture implements net.minecraft.client.renderer.MultiBufferSource {
        private final Map<RenderType, BufferBuilder> builders = new LinkedHashMap<>();
        private final Map<RenderType, TintConsumer> consumers = new LinkedHashMap<>();
        private boolean conflict;

        private void setConflict(boolean value) {
            conflict = value;
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            TintConsumer consumer = consumers.get(type);
            if (consumer == null) {
                BufferBuilder builder = new BufferBuilder(262_144);
                builder.begin(type.mode(), type.format());
                consumer = new TintConsumer(builder, this);
                builders.put(type, builder);
                consumers.put(type, consumer);
            }
            return consumer;
        }

        private void finish() {
            for (Map.Entry<RenderType, BufferBuilder> entry : builders.entrySet()) {
                BufferBuilder builder = entry.getValue();
                BufferBuilder.RenderedBuffer rendered = builder.endOrDiscardIfEmpty();
                if (rendered == null || rendered.isEmpty()) {
                    continue;
                }
                VertexBuffer gpu = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
                gpu.bind();
                gpu.upload(rendered);
                VertexBuffer.unbind();
                LAYERS.put(entry.getKey(), new Layer(entry.getKey(), gpu));
            }
            builders.clear();
            consumers.clear();
        }

        private void discard() {
            for (BufferBuilder builder : builders.values()) {
                if (builder.building()) {
                    builder.discard();
                }
            }
            builders.clear();
            consumers.clear();
        }
    }

    /** Multiplies the baked colour: translucent, slightly cold, and red where something blocks. */
    private static final class TintConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final Capture capture;

        private TintConsumer(VertexConsumer delegate, Capture capture) {
            this.delegate = delegate;
            this.capture = capture;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            delegate.color(tint(red), tint(green), tint(blue), alpha(alpha, capture.conflict));
            return this;
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
            delegate.defaultColor(tint(red), tint(green), tint(blue), alpha(alpha, capture.conflict));
        }

        @Override
        public void unsetDefaultColor() {
            delegate.unsetDefaultColor();
        }

        private static int tint(int channel) {
            return Math.max(0, Math.min(255, Math.round(channel * 0.72F)));
        }

        private static int alpha(int value, boolean conflict) {
            float scale = conflict ? 0.8F : ALPHA;
            return Math.max(0, Math.min(255, Math.round(value * scale)));
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            delegate.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            delegate.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            delegate.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            delegate.endVertex();
        }

        @Override
        public void vertex(float x, float y, float z, float red, float green, float blue, float alpha,
                           float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
            delegate.vertex(x, y, z, tint(Math.round(red * 255)) / 255.0F, tint(Math.round(green * 255)) / 255.0F,
                    tint(Math.round(blue * 255)) / 255.0F, alpha(Math.round(alpha * 255), capture.conflict) / 255.0F,
                    u, v, overlay, light, normalX, normalY, normalZ);
        }
    }
}
