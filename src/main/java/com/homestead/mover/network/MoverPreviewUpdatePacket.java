package com.homestead.mover.network;

import com.homestead.mover.client.MoverPreviewClient;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S2C: the preview moved or turned; the blocks themselves are already on the client. */
public final class MoverPreviewUpdatePacket {
    private final BlockPos origin;
    private final int yaw;

    public MoverPreviewUpdatePacket(BlockPos origin, int yaw) {
        this.origin = origin;
        this.yaw = yaw;
    }

    public static void encode(MoverPreviewUpdatePacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.origin);
        buf.writeVarInt(packet.yaw);
    }

    public static MoverPreviewUpdatePacket decode(FriendlyByteBuf buf) {
        return new MoverPreviewUpdatePacket(buf.readBlockPos(), buf.readVarInt());
    }

    public static void handle(MoverPreviewUpdatePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> MoverPreviewClient.update(packet.origin, packet.yaw)));
        ctx.get().setPacketHandled(true);
    }
}
