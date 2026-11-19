package com.homestead.mover.network;

import com.homestead.mover.flow.MoverService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S: shift + arrow keys slide the open preview one block along the ground.
 * It carries the ghost's own origin and vertical nudge, because the client walks the preview
 * under the crosshair without telling the server — sliding a stale server-side origin made the
 * ghost jump to wherever the preview was first opened (the "first up/down, then sideways" bug).
 */
public final class MoverPreviewShiftPacket {
    private final BlockPos origin;
    private final int yOffset;
    private final int dx;
    private final int dz;

    public MoverPreviewShiftPacket(BlockPos origin, int yOffset, int dx, int dz) {
        this.origin = origin;
        this.yOffset = yOffset;
        this.dx = dx;
        this.dz = dz;
    }

    public static void encode(MoverPreviewShiftPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.origin);
        buf.writeVarInt(packet.yOffset);
        buf.writeByte(packet.dx);
        buf.writeByte(packet.dz);
    }

    public static MoverPreviewShiftPacket decode(FriendlyByteBuf buf) {
        return new MoverPreviewShiftPacket(buf.readBlockPos(), buf.readVarInt(), buf.readByte(), buf.readByte());
    }

    public static void handle(MoverPreviewShiftPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                MoverService.shiftPlacement(player, packet.origin, packet.yOffset, packet.dx, packet.dz);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
