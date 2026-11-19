package com.homestead.mover.network;

import com.homestead.mover.flow.MoverService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** C2S: shift + arrow keys slide the measured corners one block along the ground. */
public final class MoverSelectionShiftPacket {
    private final int dx;
    private final int dz;

    public MoverSelectionShiftPacket(int dx, int dz) {
        this.dx = dx;
        this.dz = dz;
    }

    public static void encode(MoverSelectionShiftPacket packet, FriendlyByteBuf buf) {
        buf.writeByte(packet.dx);
        buf.writeByte(packet.dz);
    }

    public static MoverSelectionShiftPacket decode(FriendlyByteBuf buf) {
        return new MoverSelectionShiftPacket(buf.readByte(), buf.readByte());
    }

    public static void handle(MoverSelectionShiftPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                MoverService.selectionShifted(player, packet.dx, packet.dz);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
