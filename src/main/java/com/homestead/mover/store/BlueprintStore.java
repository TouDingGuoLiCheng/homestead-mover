package com.homestead.mover.store;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * One compressed file per captured building, under {@code <world>/homestead_mover/blueprints/}.
 * The case only carries the id, so a building of any size stays out of the stack NBT, and the
 * file itself is the whole building: copy it to another machine or another save and it restores
 * there unchanged (gzipped NBT, the same shape a schematic uses).
 */
public final class BlueprintStore {
    /** Folder inside the world save, next to {@code region} and {@code data}. */
    public static final String FOLDER = "homestead_mover/blueprints";
    /** Mod-specific file type: gzipped NBT, one building each. */
    public static final String EXTENSION = ".hsm";
    public static final String FORMAT = "homestead_mover/blueprint";
    public static final int VERSION = 1;

    private static final Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    /** Ids are file names, so nothing that could climb out of the folder is allowed through. */
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    private final Path folder;

    private BlueprintStore(Path folder) {
        this.folder = folder;
    }

    /** Store of this save; also folds an old single-file store into one file per building once. */
    public static BlueprintStore get(MinecraftServer server) {
        Path root = server.getWorldPath(LevelResource.ROOT);
        BlueprintStore store = new BlueprintStore(root.resolve("homestead_mover").resolve("blueprints"));
        store.migrateLegacy(root.resolve("data").resolve("homestead_mover_blueprints.dat"));
        return store;
    }

    public Path folder() {
        return folder;
    }

    public static boolean isSafeId(String id) {
        return id != null && SAFE_ID.matcher(id).matches() && !id.contains("..");
    }

    public Path fileOf(String id) {
        return folder.resolve(id + EXTENSION);
    }

    public boolean exists(String id) {
        return isSafeId(id) && Files.isRegularFile(fileOf(id));
    }

    /** The stored snapshot, or an empty tag when the file is gone or unreadable. */
    public CompoundTag load(String id) {
        if (!isSafeId(id)) {
            return new CompoundTag();
        }
        Path file = fileOf(id);
        if (!Files.isRegularFile(file)) {
            return new CompoundTag();
        }
        try {
            return unwrap(readFile(file));
        } catch (IOException | RuntimeException broken) {
            LOGGER.warn("homestead_mover: unreadable blueprint {}", file, broken);
            return new CompoundTag();
        }
    }

    /** Writes the building as its own file; false means the disk refused and nothing was stored. */
    public boolean save(String id, CompoundTag snapshot) {
        if (!isSafeId(id)) {
            return false;
        }
        try {
            Files.createDirectories(folder);
            writeFile(fileOf(id), wrap(id, snapshot == null ? new CompoundTag() : snapshot.copy()));
            return true;
        } catch (IOException | RuntimeException failure) {
            LOGGER.error("homestead_mover: could not write blueprint {}", id, failure);
            return false;
        }
    }

    public void remove(String id) {
        if (!isSafeId(id)) {
            return;
        }
        try {
            Files.deleteIfExists(fileOf(id));
        } catch (IOException failure) {
            LOGGER.warn("homestead_mover: could not delete blueprint {}", id, failure);
        }
    }

    /** Every file in the folder, by id, in a stable order. */
    public Set<String> ids() {
        Set<String> ids = new TreeSet<>();
        if (!Files.isDirectory(folder)) {
            return ids;
        }
        try (var stream = Files.list(folder)) {
            stream.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(EXTENSION))
                    .map(name -> name.substring(0, name.length() - EXTENSION.length()))
                    .filter(BlueprintStore::isSafeId)
                    .forEach(ids::add);
        } catch (IOException failure) {
            LOGGER.warn("homestead_mover: could not list {}", folder, failure);
        }
        return ids;
    }

    /** Heading lines for the import screen: what each file calls itself and how big it is. */
    public List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        for (String id : ids()) {
            Path file = fileOf(id);
            try {
                CompoundTag snapshot = unwrap(readFile(file));
                int[] size = snapshot.getIntArray("Size");
                entries.add(new Entry(id, snapshot.getString("Name"), snapshot.getString("Dimension"),
                        size.length == 3 ? size[0] : 0, size.length == 3 ? size[1] : 0,
                        size.length == 3 ? size[2] : 0, Files.size(file)));
            } catch (IOException | RuntimeException broken) {
                LOGGER.warn("homestead_mover: skipping unreadable blueprint {}", file, broken);
            }
        }
        return entries;
    }

    public record Entry(String id, String name, String dimension, int sizeX, int sizeY, int sizeZ, long bytes) {
    }

    // ---- file shape ----

    private static CompoundTag wrap(String id, CompoundTag snapshot) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Format", FORMAT);
        tag.putInt("Version", VERSION);
        tag.putString("Id", id);
        tag.putLong("SavedAt", System.currentTimeMillis());
        tag.put("Snapshot", snapshot);
        return tag;
    }

    /** Accepts our own wrapper and also a bare snapshot, so files from other tools still read. */
    private static CompoundTag unwrap(CompoundTag tag) {
        CompoundTag inner = tag.getCompound("Snapshot");
        return inner.isEmpty() ? tag : inner;
    }

    private static CompoundTag readFile(Path path) throws IOException {
        try (InputStream raw = Files.newInputStream(path);
             DataInputStream in = new DataInputStream(new GZIPInputStream(raw))) {
            return NbtIo.read(in);
        }
    }

    private static void writeFile(Path path, CompoundTag tag) throws IOException {
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        try (OutputStream raw = Files.newOutputStream(temp);
             DataOutputStream out = new DataOutputStream(new GZIPOutputStream(raw))) {
            NbtIo.write(tag, out);
        }
        try {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException notAtomic) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * The old build stored every building inside one SavedData file. Split it into one file each
     * on first use and keep the old file as {@code .migrated}: nobody loses a building, and the
     * game never reads that file again.
     */
    private void migrateLegacy(Path legacy) {
        if (Files.isRegularFile(legacy)) {
            migrateFrom(legacy);
            return;
        }
        // an earlier attempt may have renamed the store away before writing anything: heal that
        if (!ids().isEmpty()) {
            return;
        }
        Path backup = legacy.resolveSibling(legacy.getFileName() + ".migrated");
        if (Files.isRegularFile(backup)) {
            migrateFrom(backup);
        }
    }

    private void migrateFrom(Path source) {
        try {
            CompoundTag file = readFile(source);
            // SavedData wraps everything in a "data" compound; a bare store starts at the root
            CompoundTag payload = file.contains("data", Tag.TAG_COMPOUND) ? file.getCompound("data") : file;
            ListTag list = rows(payload);
            Files.createDirectories(folder);
            int written = 0;
            for (int i = 0; i < list.size(); i++) {
                CompoundTag row = list.getCompound(i);
                String id = row.getString("Id");
                if (!isSafeId(id) || Files.exists(fileOf(id))) {
                    continue;
                }
                writeFile(fileOf(id), wrap(id, row.getCompound("Data")));
                written++;
            }
            if (written == 0) {
                LOGGER.warn("homestead_mover: {} held no building that was not already on disk", source);
                return;
            }
            if (source.getFileName().toString().endsWith(".dat")) {
                Files.move(source, source.resolveSibling(source.getFileName() + ".migrated"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            LOGGER.info("homestead_mover: split {} building(s) out of {} into {}", written, source, folder);
        } catch (IOException | RuntimeException failure) {
            LOGGER.warn("homestead_mover: could not migrate {}", source, failure);
        }
    }

    /** The list may be called "Buildings" or anything else a build of this mod once wrote. */
    private static ListTag rows(CompoundTag payload) {
        ListTag list = payload.getList("Buildings", Tag.TAG_COMPOUND);
        if (!list.isEmpty()) {
            return list;
        }
        for (String key : payload.getAllKeys()) {
            if (payload.getTagType(key) != Tag.TAG_LIST) {
                continue;
            }
            ListTag candidate = payload.getList(key, Tag.TAG_COMPOUND);
            if (!candidate.isEmpty() && candidate.getCompound(0).contains("Id", Tag.TAG_STRING)) {
                return candidate;
            }
        }
        return list;
    }
}
