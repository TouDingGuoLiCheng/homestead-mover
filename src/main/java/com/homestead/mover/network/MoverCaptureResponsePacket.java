package com.homestead.mover.network;

import com.homestead.mover.flow.MoverService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** C2S: the confirm screen's answer. False also comes from Esc. */
public final class MoverCaptureResponsePacket {
    /** Longer than any sane build name; also what the box allows. */
    public static final int MAX_NAME_LENGTH = 48;

    private final boolean confirmed;
    private final String name;

    public MoverCaptureResponsePacket(boolean confirmed) {
        this(confirmed, "");
    }

    public MoverCaptureResponsePacket(boolean confirmed, String name) {
        this.confirmed = confirmed;
        this.name = name == null ? "" : name.length() > MAX_NAME_LENGTH
                ? name.substring(0, MAX_NAME_LENGTH) : name;
    }

    public boolean confirmed() {
        return confirmed;
    }

    public String name() {
        return name;
    }

    public static void encode(MoverCaptureResponsePacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.confirmed);
        buf.writeUtf(packet.name, MAX_NAME_LENGTH);
    }

    public static MoverCaptureResponsePacket decode(FriendlyByteBuf buf) {
        return new MoverCaptureResponsePacket(buf.readBoolean(), buf.readUtf(MAX_NAME_LENGTH));
    }

    public static void handle(MoverCaptureResponsePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                MoverService.resolveCapture(player, packet.confirmed, packet.name());
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
