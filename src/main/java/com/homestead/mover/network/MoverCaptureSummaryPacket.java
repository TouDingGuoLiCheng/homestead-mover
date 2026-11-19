package com.homestead.mover.network;

import com.homestead.mover.snapshot.CapturedBuilding;
import com.homestead.mover.client.MoverCaptureScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** S2C: what the capture found, so the player can look before the spot is emptied. */
public final class MoverCaptureSummaryPacket {
    /** Why an entity is being left behind. */
    public static final int REASON_PLAYER = 0;
    public static final int REASON_MOD_ENTITY = 1;
    public static final int REASON_NOT_LIVING = 2;
    public static final int REASON_TRANSIENT = 3;

    public record SkipEntry(String key, int reason, int count) {
    }

    private final String name;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final int volume;
    private final int nonAir;
    private final int tiles;
    private final int entities;
    private final List<SkipEntry> skipped;

    public MoverCaptureSummaryPacket(String name, int sizeX, int sizeY, int sizeZ, int volume,
                                     int nonAir, int tiles, int entities, List<SkipEntry> skipped) {
        this.name = name == null ? "" : name;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.volume = volume;
        this.nonAir = nonAir;
        this.tiles = tiles;
        this.entities = entities;
        this.skipped = List.copyOf(skipped);
    }

    public static MoverCaptureSummaryPacket of(CapturedBuilding building) {
        List<SkipEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : building.snapshot().skipped().entrySet()) {
            entries.add(new SkipEntry(entry.getKey(), reasonOf(entry.getKey()), entry.getValue()));
        }
        return new MoverCaptureSummaryPacket(building.snapshot().name(),
                building.snapshot().region().sizeX(), building.snapshot().region().sizeY(),
                building.snapshot().region().sizeZ(), building.snapshot().region().volume(),
                building.snapshot().region().nonAirCount(), building.tileEntityCount(),
                building.entityCount(), entries);
    }

    /** The capture groups by reason ("player"/"not_living"/"transient") or by namespace. */
    private static int reasonOf(String key) {
        return switch (key) {
            case "player" -> REASON_PLAYER;
            case "not_living" -> REASON_NOT_LIVING;
            case "transient" -> REASON_TRANSIENT;
            default -> REASON_MOD_ENTITY;
        };
    }

    public String name() {
        return name;
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public int volume() {
        return volume;
    }

    public int nonAir() {
        return nonAir;
    }

    public int tiles() {
        return tiles;
    }

    public int entities() {
        return entities;
    }

    public List<SkipEntry> skipped() {
        return skipped;
    }

    public static void encode(MoverCaptureSummaryPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.name);
        buf.writeVarInt(packet.sizeX);
        buf.writeVarInt(packet.sizeY);
        buf.writeVarInt(packet.sizeZ);
        buf.writeVarInt(packet.volume);
        buf.writeVarInt(packet.nonAir);
        buf.writeVarInt(packet.tiles);
        buf.writeVarInt(packet.entities);
        buf.writeVarInt(packet.skipped.size());
        for (SkipEntry entry : packet.skipped) {
            buf.writeUtf(entry.key());
            buf.writeVarInt(entry.reason());
            buf.writeVarInt(entry.count());
        }
    }

    public static MoverCaptureSummaryPacket decode(FriendlyByteBuf buf) {
        String name = buf.readUtf();
        int sizeX = buf.readVarInt();
        int sizeY = buf.readVarInt();
        int sizeZ = buf.readVarInt();
        int volume = buf.readVarInt();
        int nonAir = buf.readVarInt();
        int tiles = buf.readVarInt();
        int entities = buf.readVarInt();
        int count = buf.readVarInt();
        List<SkipEntry> skipped = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            skipped.add(new SkipEntry(buf.readUtf(), buf.readVarInt(), buf.readVarInt()));
        }
        return new MoverCaptureSummaryPacket(name, sizeX, sizeY, sizeZ, volume, nonAir, tiles, entities, skipped);
    }

    public static void handle(MoverCaptureSummaryPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> MoverCaptureScreen.show(packet)));
        ctx.get().setPacketHandled(true);
    }
}
