package com.homestead.mover.network;

import com.homestead.mover.flow.MoverService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** C2S: keys the server still has to know about (selection height, plus the legacy preview actions). */
public final class MoverPreviewActionPacket {
    public enum Action {
        ROTATE_RIGHT,
        ROTATE_LEFT,
        NUDGE_UP,
        NUDGE_DOWN,
        /** Arrow keys while measuring: grow or shrink the box upwards from its floor. */
        SELECTION_TALLER,
        SELECTION_SHORTER
    }

    private final Action action;

    public MoverPreviewActionPacket(Action action) {
        this.action = action;
    }

    public static void encode(MoverPreviewActionPacket packet, FriendlyByteBuf buf) {
        buf.writeEnum(packet.action);
    }

    public static MoverPreviewActionPacket decode(FriendlyByteBuf buf) {
        return new MoverPreviewActionPacket(buf.readEnum(Action.class));
    }

    public static void handle(MoverPreviewActionPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                MoverService.applyPreviewAction(player, packet.action);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
