package com.homestead.mover.network;

import com.homestead.mover.flow.MoverService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S: the player left clicked a block while holding an empty case. The click is sent as its own
 * message instead of riding on vanilla's left click, because a click the server merely cancels
 * still lets the client keep mining — and once the case's tag changes the client re-sends
 * START_DESTROY_BLOCK every tick, which used to record two corners per click (a permanent 1x1x1).
 */
public final class MoverSelectionClickPacket {
    private final BlockPos pos;

    public MoverSelectionClickPacket(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(MoverSelectionClickPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.pos);
    }

    public static MoverSelectionClickPacket decode(FriendlyByteBuf buf) {
        return new MoverSelectionClickPacket(buf.readBlockPos());
    }

    public static void handle(MoverSelectionClickPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                MoverService.cornerClicked(player, packet.pos);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
