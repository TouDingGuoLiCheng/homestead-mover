package com.homestead.mover.network;

import com.homestead.mover.flow.MoverService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** C2S: the file the player picked in the import screen. */
public final class MoverImportPickPacket {
    private final String id;

    public MoverImportPickPacket(String id) {
        this.id = id == null ? "" : id;
    }

    public static void encode(MoverImportPickPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.id);
    }

    public static MoverImportPickPacket decode(FriendlyByteBuf buf) {
        return new MoverImportPickPacket(buf.readUtf());
    }

    public static void handle(MoverImportPickPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                MoverService.importBuilding(player, packet.id);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
