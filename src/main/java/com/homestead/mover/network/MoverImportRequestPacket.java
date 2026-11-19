package com.homestead.mover.network;

import com.homestead.mover.flow.MoverService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** C2S: "I" with an empty case — ask the server what .hsm files this save has. */
public final class MoverImportRequestPacket {
    public MoverImportRequestPacket() {
    }

    public static void encode(MoverImportRequestPacket packet, FriendlyByteBuf buf) {
    }

    public static MoverImportRequestPacket decode(FriendlyByteBuf buf) {
        return new MoverImportRequestPacket();
    }

    public static void handle(MoverImportRequestPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                MoverService.requestImport(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
