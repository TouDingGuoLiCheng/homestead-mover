package com.homestead.mover.placement;

// Adapted from 车万女仆：施工队 (placement/ConflictScanner.java) on 2026-10-26; TLM and terminal
// dependencies stripped, their Blueprint/BlueprintRegion types replaced by 整栋搬家's
// RegionSnapshot so the same algorithms run on our own snapshot format.

import com.homestead.mover.snapshot.RawBlockState;
import com.homestead.mover.snapshot.RegionSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Evaluates placement conflicts against the world. */
public final class ConflictScanner {
    private static final int MAX_SAMPLE_POSITIONS = 64;
    private static final int MAX_BLOCK_TYPES_IN_SUMMARY = 3;
    private static final int MAX_SAMPLE_COORDS = 2;

    private ConflictScanner() {
    }

    public record Result(int conflictCount, List<BlockPos> sampleConflicts, Map<Block, Integer> blockingBlocks) {
        public Result {
            sampleConflicts = List.copyOf(sampleConflicts);
            blockingBlocks = Map.copyOf(blockingBlocks);
        }

        public boolean hasConflicts() {
            return conflictCount > 0;
        }

        public String blockingSummary() {
            if (blockingBlocks.isEmpty()) {
                return String.valueOf(conflictCount);
            }
            List<Map.Entry<Block, Integer>> sorted = blockingBlocks.entrySet().stream()
                    .sorted(Map.Entry.<Block, Integer>comparingByValue().reversed())
                    .limit(MAX_BLOCK_TYPES_IN_SUMMARY)
                    .toList();
            StringBuilder sb = new StringBuilder();
            int shown = 0;
            for (Map.Entry<Block, Integer> entry : sorted) {
                if (sb.length() > 0) {
                    sb.append("、");
                }
                sb.append(entry.getKey().getName().getString()).append("×").append(entry.getValue());
                shown += entry.getValue();
            }
            if (conflictCount > shown) {
                sb.append("…");
            }
            return sb.toString();
        }

        /** "12,65,-4 / 13,65,-4" — the first blocked spots, so the player can walk right to them. */
        public String sampleCoords() {
            StringBuilder sb = new StringBuilder();
            int shown = 0;
            for (BlockPos pos : sampleConflicts) {
                if (shown >= MAX_SAMPLE_COORDS) {
                    break;
                }
                if (sb.length() > 0) {
                    sb.append(" / ");
                }
                sb.append(pos.getX()).append(',').append(pos.getY()).append(',').append(pos.getZ());
                shown++;
            }
            return sb.toString();
        }

        /** Count first, then what it is, then where — short enough to read in one glance. */
        public Component blockingMessage() {
            String where = sampleCoords();
            if (where.isEmpty()) {
                return Component.translatable("message.homestead_mover.placement_blocked", conflictCount, blockingSummary());
            }
            return Component.translatable("message.homestead_mover.placement_blocked_at", conflictCount, blockingSummary(), where);
        }
    }

    /** Counts placed cells that would overwrite something already standing there. */
    public static Result scan(BlockGetter level, RegionSnapshot region, BlockPos origin, int yaw) {
        int conflicts = 0;
        List<BlockPos> samples = new ArrayList<>();
        Map<Block, Integer> blocking = new LinkedHashMap<>();
        for (int y = 0; y < region.sizeY(); y++) {
            for (int z = 0; z < region.sizeZ(); z++) {
                for (int x = 0; x < region.sizeX(); x++) {
                    RawBlockState raw = region.blockAt(x, y, z);
                    BlockState planned = BlockStateResolve.resolveRotated(raw, yaw);
                    if (planned.isAir()) {
                        continue;
                    }
                    BlockPos world = origin.offset(
                            SchematicYaw.offsetX(x, z, region.sizeX(), region.sizeZ(), yaw), y,
                            SchematicYaw.offsetZ(x, z, region.sizeX(), region.sizeZ(), yaw));
                    BlockState existing = level.getBlockState(world);
                    if (PlacementRules.isConflict(existing, planned, false, false)) {
                        conflicts++;
                        blocking.merge(existing.getBlock(), 1, Integer::sum);
                        if (samples.size() < MAX_SAMPLE_POSITIONS) {
                            samples.add(world.immutable());
                        }
                    }
                }
            }
        }
        return new Result(conflicts, samples, blocking);
    }
}
