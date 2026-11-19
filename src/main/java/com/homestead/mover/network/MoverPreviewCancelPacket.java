package com.homestead.mover.network;

import com.homestead.mover.flow.MoverService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** C2S: right click backs out of the preview and keeps the building in the case. */
public final class MoverPreviewCancelPacket {
    public MoverPreviewCancelPacket() {
    }

    public static void encode(MoverPreviewCancelPacket packet, FriendlyByteBuf buf) {
    }

    public static MoverPreviewCancelPacket decode(FriendlyByteBuf buf) {
        return new MoverPreviewCancelPacket();
    }

    public static void handle(MoverPreviewCancelPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                MoverService.cancelPlacement(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
