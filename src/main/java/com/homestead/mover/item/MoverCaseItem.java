package com.homestead.mover.item;

import com.homestead.mover.capture.Bounds;
import com.homestead.mover.snapshot.MoveSnapshot;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * The case: empty it measures a building, filled it carries the building's id.
 * Only the id lives on the stack — the blocks live in {@code BlueprintStore}.
 */
public class MoverCaseItem extends Item {
    public static final String KEY_ID = "BlueprintId";
    public static final String KEY_NAME = "BlueprintName";
    public static final String KEY_BLOCKS = "BlueprintBlocks";
    public static final String KEY_ENTITIES = "BlueprintEntities";
    public static final String KEY_TILES = "BlueprintTiles";
    public static final String KEY_SIZE = "BlueprintSize";
    /**
     * Selection: up to two ground corners (each keeps the Y it was clicked on) plus a height that
     * grows up from the first corner's floor.
     */
    public static final String KEY_CORNER_1 = "Corner1";
    public static final String KEY_CORNER_2 = "Corner2";
    public static final String KEY_HEIGHT = "AreaHeight";
    /** 1.20.1 worlds are 384 tall, so nothing higher could be selected anyway. */
    public static final int MAX_HEIGHT = 384;

    public MoverCaseItem(Properties properties) {
        super(properties);
    }

    // ---- state ----

    public static boolean isBound(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(KEY_ID, Tag.TAG_STRING);
    }

    public static String blueprintId(ItemStack stack) {
        return isBound(stack) ? stack.getTag().getString(KEY_ID) : "";
    }

    /** What one corner click did, so the flow can say it in one line. */
    public enum CornerChange {
        /** The first corner is now standing. */
        FIRST,
        /** The second corner closed the area. */
        SECOND,
        /** Both corners were taken, so this click only moved the second one. */
        REPLACED_SECOND,
        /** The click landed on a corner that was already picked, so that corner is gone. */
        DROPPED
    }

    /** The empty case the player is holding (main hand first, then the off hand), or {@code EMPTY}. */
    public static ItemStack heldCase(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof MoverCaseItem && !isBound(main)) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof MoverCaseItem && !isBound(off)) {
            return off;
        }
        return ItemStack.EMPTY;
    }

    /**
     * The case in hand whatever it carries — importing overwrites a binding, so a case that is
     * still pointing at a missing blueprint has to be reachable too.
     */
    public static ItemStack heldAnyCase(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof MoverCaseItem) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        return off.getItem() instanceof MoverCaseItem ? off : ItemStack.EMPTY;
    }

    private static void writeCorner(CompoundTag tag, String key, BlockPos pos) {
        tag.putIntArray(key, new int[] {pos.getX(), pos.getY(), pos.getZ()});
    }

    private static BlockPos readCorner(CompoundTag tag, String key) {
        if (tag == null || !tag.contains(key, Tag.TAG_INT_ARRAY)) {
            return null;
        }
        int[] value = tag.getIntArray(key);
        // two-int corners came from the old XZ-only layout and never carried a floor
        return value.length < 3 ? null : new BlockPos(value[0], value[1], value[2]);
    }

    private static boolean sameColumn(BlockPos corner, BlockPos pos) {
        return corner.getX() == pos.getX() && corner.getZ() == pos.getZ();
    }

    /** How many corners the selection has right now: 0, 1 or 2. */
    public static int cornerCount(ItemStack stack) {
        if (!stack.hasTag()) {
            return 0;
        }
        CompoundTag tag = stack.getTag();
        return (readCorner(tag, KEY_CORNER_1) == null ? 0 : 1) + (readCorner(tag, KEY_CORNER_2) == null ? 0 : 1);
    }

    /** The corner in that slot (0 or 1), or null. */
    public static BlockPos corner(ItemStack stack, int index) {
        if (!stack.hasTag()) {
            return null;
        }
        return readCorner(stack.getTag(), index == 0 ? KEY_CORNER_1 : KEY_CORNER_2);
    }

    /**
     * One click, one corner. The first click stands a corner, the second closes the area, and once
     * both are taken a click elsewhere only moves the second one. Clicking a corner that is
     * already picked drops it instead, and the survivor steps up into the first slot — so the
     * count moves by one per click and the corner that stayed keeps the floor it was picked on.
     */
    public static CornerChange selectCorner(ItemStack stack, BlockPos pos) {
        CompoundTag tag = stack.getOrCreateTag();
        BlockPos first = readCorner(tag, KEY_CORNER_1);
        BlockPos second = readCorner(tag, KEY_CORNER_2);
        if (first != null && sameColumn(first, pos)) {
            if (second == null) {
                tag.remove(KEY_CORNER_1);
                tag.remove(KEY_HEIGHT);
            } else {
                writeCorner(tag, KEY_CORNER_1, second);
                tag.remove(KEY_CORNER_2);
            }
            return CornerChange.DROPPED;
        }
        if (second != null && sameColumn(second, pos)) {
            tag.remove(KEY_CORNER_2);
            return CornerChange.DROPPED;
        }
        if (first == null) {
            writeCorner(tag, KEY_CORNER_1, pos);
            tag.putInt(KEY_HEIGHT, 1);
            return CornerChange.FIRST;
        }
        boolean replacing = second != null;
        writeCorner(tag, KEY_CORNER_2, pos);
        return replacing ? CornerChange.REPLACED_SECOND : CornerChange.SECOND;
    }

    /** Slides both corners along the ground, each keeping its own floor Y. */
    public static boolean shiftCorners(ItemStack stack, int dx, int dz) {
        if (dx == 0 && dz == 0) {
            return false;
        }
        CompoundTag tag = stack.hasTag() ? stack.getTag() : null;
        BlockPos first = readCorner(tag, KEY_CORNER_1);
        BlockPos second = readCorner(tag, KEY_CORNER_2);
        if (first == null) {
            return false;
        }
        writeCorner(tag, KEY_CORNER_1, first.offset(dx, 0, dz));
        if (second != null) {
            writeCorner(tag, KEY_CORNER_2, second.offset(dx, 0, dz));
        }
        return true;
    }

    /** Grows or shrinks the selection upwards; returns the height it ended up with. */
    public static int adjustHeight(ItemStack stack, int delta) {
        CompoundTag tag = stack.getOrCreateTag();
        int height = Math.max(1, Math.min(MAX_HEIGHT, tag.getInt(KEY_HEIGHT) + delta));
        tag.putInt(KEY_HEIGHT, height);
        return height;
    }

    public static int selectionHeight(ItemStack stack) {
        return stack.hasTag() ? Math.max(1, stack.getTag().getInt(KEY_HEIGHT)) : 1;
    }

    public static Optional<Bounds> selection(ItemStack stack) {
        if (!stack.hasTag()) {
            return Optional.empty();
        }
        CompoundTag tag = stack.getTag();
        BlockPos first = readCorner(tag, KEY_CORNER_1);
        BlockPos second = readCorner(tag, KEY_CORNER_2);
        if (first == null || second == null) {
            return Optional.empty();
        }
        // the floor is the first corner's own Y: the second corner only spans the horizontal area
        int floorY = first.getY();
        int height = Math.max(1, Math.min(MAX_HEIGHT, tag.getInt(KEY_HEIGHT)));
        return Optional.of(new Bounds(Math.min(first.getX(), second.getX()), floorY,
                Math.min(first.getZ(), second.getZ()),
                Math.abs(first.getX() - second.getX()) + 1, height,
                Math.abs(first.getZ() - second.getZ()) + 1));
    }

    /** "3x3x5（45 格）" for chat and HUD; empty while only one corner is set. */
    public static String selectionSummary(ItemStack stack) {
        return selection(stack)
                .map(bounds -> bounds.sizeX() + "x" + bounds.sizeY() + "x" + bounds.sizeZ()
                        + "（" + bounds.volume() + " 格）")
                .orElse("");
    }

    public static void clearSelection(ItemStack stack) {
        if (stack.hasTag()) {
            CompoundTag tag = stack.getTag();
            tag.remove(KEY_CORNER_1);
            tag.remove(KEY_CORNER_2);
            tag.remove(KEY_HEIGHT);
        }
    }

    public static void bind(ItemStack stack, String id, MoveSnapshot snapshot) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(KEY_ID, id);
        tag.putString(KEY_NAME, snapshot.name());
        tag.putInt(KEY_BLOCKS, snapshot.region().nonAirCount());
        tag.putInt(KEY_TILES, 0);
        tag.putInt(KEY_ENTITIES, 0);
        tag.putString(KEY_SIZE, snapshot.region().sizeX() + "x" + snapshot.region().sizeY()
                + "x" + snapshot.region().sizeZ());
        clearSelection(stack);
    }

    public static void bindCounts(ItemStack stack, int tiles, int entities) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(KEY_TILES, tiles);
        tag.putInt(KEY_ENTITIES, entities);
    }

    /** Restoring puts the case back to empty so the same item can be used again. */
    public static void unbind(ItemStack stack) {
        if (stack.hasTag()) {
            CompoundTag tag = stack.getTag();
            tag.remove(KEY_ID);
            tag.remove(KEY_NAME);
            tag.remove(KEY_BLOCKS);
            tag.remove(KEY_TILES);
            tag.remove(KEY_ENTITIES);
            tag.remove(KEY_SIZE);
            clearSelection(stack);
        }
    }

    // ---- display ----

    @Override
    public Component getName(ItemStack stack) {
        if (!isBound(stack)) {
            return super.getName(stack);
        }
        CompoundTag tag = stack.getTag();
        return Component.translatable("item.homestead_mover.mover_case.filled",
                tag.getString(KEY_NAME), tag.getInt(KEY_BLOCKS));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        if (!isBound(stack)) {
            tooltip.add(Component.translatable("item.homestead_mover.mover_case.hint")
                    .withStyle(ChatFormatting.GRAY));
            selection(stack).ifPresent(bounds -> tooltip.add(Component
                    .translatable("item.homestead_mover.mover_case.selection",
                            bounds.sizeX(), bounds.sizeY(), bounds.sizeZ(), bounds.volume())
                    .withStyle(ChatFormatting.YELLOW)));
            return;
        }
        CompoundTag tag = stack.getTag();
        tooltip.add(Component.translatable("item.homestead_mover.mover_case.size", tag.getString(KEY_SIZE))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.homestead_mover.mover_case.contents",
                        tag.getInt(KEY_BLOCKS), tag.getInt(KEY_TILES), tag.getInt(KEY_ENTITIES))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.homestead_mover.mover_case.restore_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
