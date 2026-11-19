package com.homestead.mover.network;

import com.homestead.mover.client.MoverPreviewClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S2C: drop the ghost, either because it landed or because the player backed out. */
public final class MoverPreviewClearPacket {
    public MoverPreviewClearPacket() {
    }

    public static void encode(MoverPreviewClearPacket packet, FriendlyByteBuf buf) {
    }

    public static MoverPreviewClearPacket decode(FriendlyByteBuf buf) {
        return new MoverPreviewClearPacket();
    }

    public static void handle(MoverPreviewClearPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> MoverPreviewClient::clear));
        ctx.get().setPacketHandled(true);
    }
}
