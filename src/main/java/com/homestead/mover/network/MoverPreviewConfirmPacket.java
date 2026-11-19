package com.homestead.mover.network;

import com.homestead.mover.flow.MoverService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S: left click lands the preview. The client sends where its ghost actually is, because the
 * ghost follows the crosshair locally; the server re-checks conflicts before it places anything.
 */
public final class MoverPreviewConfirmPacket {
    private final BlockPos origin;
    private final int yaw;
    private final int yOffset;

    public MoverPreviewConfirmPacket(BlockPos origin, int yaw, int yOffset) {
        this.origin = origin;
        this.yaw = yaw;
        this.yOffset = yOffset;
    }

    public static void encode(MoverPreviewConfirmPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.origin);
        buf.writeVarInt(packet.yaw);
        buf.writeVarInt(packet.yOffset);
    }

    public static MoverPreviewConfirmPacket decode(FriendlyByteBuf buf) {
        return new MoverPreviewConfirmPacket(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(MoverPreviewConfirmPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                MoverService.confirmPlacementAt(player, packet.origin, packet.yaw, packet.yOffset);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
