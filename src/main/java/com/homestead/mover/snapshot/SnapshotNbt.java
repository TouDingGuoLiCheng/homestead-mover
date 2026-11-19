package com.homestead.mover.snapshot;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** NBT codec for captured buildings. Pure data in, pure data out — no world access. */
public final class SnapshotNbt {
    private static final int FORMAT = 1;

    private SnapshotNbt() {
    }

    public static CompoundTag encode(CapturedBuilding building) {
        MoveSnapshot snapshot = building.snapshot();
        RegionSnapshot region = snapshot.region();

        CompoundTag tag = new CompoundTag();
        tag.putInt("Format", FORMAT);
        tag.putString("Name", snapshot.name());
        tag.putString("Dimension", snapshot.dimensionId());
        tag.putIntArray("Origin", new int[] {snapshot.originX(), snapshot.originY(), snapshot.originZ()});
        tag.putIntArray("Size", new int[] {region.sizeX(), region.sizeY(), region.sizeZ()});

        ListTag paletteTag = new ListTag();
        for (RawBlockState state : region.palette().states()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Name", state.name());
            if (!state.properties().isEmpty()) {
                CompoundTag props = new CompoundTag();
                state.properties().forEach(props::putString);
                entry.put("Properties", props);
            }
            paletteTag.add(entry);
        }
        tag.put("Palette", paletteTag);

        tag.putInt("Bits", region.states().bitsPerEntry());
        tag.putLongArray("States", region.states().copyData());

        CompoundTag skippedTag = new CompoundTag();
        snapshot.skipped().forEach(skippedTag::putInt);
        tag.put("Skipped", skippedTag);

        tag.put("TileEntities", building.tileEntities());
        tag.put("Entities", building.entities());
        return tag;
    }

    public static CapturedBuilding decode(CompoundTag tag) {
        int format = tag.getInt("Format");
        if (format != FORMAT) {
            throw new IllegalArgumentException("unsupported snapshot format: " + format);
        }
        int[] origin = tag.getIntArray("Origin");
        int[] size = tag.getIntArray("Size");
        if (origin.length != 3 || size.length != 3) {
            throw new IllegalArgumentException("origin/size must have three components");
        }

        ListTag paletteTag = tag.getList("Palette", Tag.TAG_COMPOUND);
        if (paletteTag.isEmpty()) {
            throw new IllegalArgumentException("empty palette");
        }
        List<RawBlockState> states = new ArrayList<>(paletteTag.size());
        for (int i = 0; i < paletteTag.size(); i++) {
            CompoundTag entry = paletteTag.getCompound(i);
            String name = entry.getString("Name");
            if (name.isEmpty()) {
                throw new IllegalArgumentException("palette entry " + i + " has no name");
            }
            Map<String, String> properties = new LinkedHashMap<>();
            CompoundTag props = entry.getCompound("Properties");
            for (String key : props.getAllKeys()) {
                properties.put(key, props.getString(key));
            }
            states.add(new RawBlockState(name, properties));
        }

        Palette palette = Palette.ofOrdered(states);
        int volume = size[0] * size[1] * size[2];
        PackedStates packed = new PackedStates(tag.getLongArray("States"), tag.getInt("Bits"), volume);
        RegionSnapshot region = new RegionSnapshot(size[0], size[1], size[2], palette, packed);

        Map<String, Integer> skipped = new LinkedHashMap<>();
        CompoundTag skippedTag = tag.getCompound("Skipped");
        for (String key : skippedTag.getAllKeys()) {
            skipped.put(key, skippedTag.getInt(key));
        }

        MoveSnapshot snapshot = new MoveSnapshot(tag.getString("Name"), tag.getString("Dimension"),
                origin[0], origin[1], origin[2], region, skipped);
        return new CapturedBuilding(snapshot,
                tag.getList("TileEntities", Tag.TAG_COMPOUND),
                tag.getList("Entities", Tag.TAG_COMPOUND));
    }
}
