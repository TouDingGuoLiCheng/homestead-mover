package com.homestead.mover.network;

import com.homestead.mover.client.MoverImportScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** S2C: the blueprint files this save can import, so the player can pick one by name. */
public final class MoverImportListPacket {
    public record Entry(String id, String name, String dimension, int sizeX, int sizeY, int sizeZ, long bytes) {
    }

    private final List<Entry> entries;

    public MoverImportListPacket(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    public List<Entry> entries() {
        return entries;
    }

    public static void encode(MoverImportListPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entries.size());
        for (Entry entry : packet.entries) {
            buf.writeUtf(entry.id());
            buf.writeUtf(entry.name());
            buf.writeUtf(entry.dimension());
            buf.writeVarInt(entry.sizeX());
            buf.writeVarInt(entry.sizeY());
            buf.writeVarInt(entry.sizeZ());
            buf.writeLong(entry.bytes());
        }
    }

    public static MoverImportListPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new Entry(buf.readUtf(), buf.readUtf(), buf.readUtf(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readLong()));
        }
        return new MoverImportListPacket(entries);
    }

    public static void handle(MoverImportListPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> MoverImportScreen.show(packet)));
        ctx.get().setPacketHandled(true);
    }
}
