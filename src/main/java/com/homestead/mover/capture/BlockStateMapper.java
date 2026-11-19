package com.homestead.mover.capture;

import com.homestead.mover.placement.BlockStateResolve;
import com.homestead.mover.snapshot.RawBlockState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.LinkedHashMap;
import java.util.Map;

/** Block state <-> {@link RawBlockState}, the only place that touches the block registry. */
public final class BlockStateMapper {
    private BlockStateMapper() {
    }

    public static RawBlockState rawOf(BlockState state) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (state.getProperties().isEmpty()) {
            return RawBlockState.of(key.toString());
        }
        Map<String, String> properties = new LinkedHashMap<>();
        for (Property<?> property : state.getProperties()) {
            properties.put(property.getName(), nameOf(state, property));
        }
        return new RawBlockState(key.toString(), properties);
    }

    /**
     * Missing or malformed ids degrade to air instead of throwing inside a tick.
     * The body lives in {@link com.homestead.mover.placement.BlockStateResolve} (adapted from
     * 施工队) so there is only one resolver to keep honest.
     */
    public static BlockState resolve(RawBlockState raw) {
        return BlockStateResolve.resolve(raw);
    }

    private static <T extends Comparable<T>> String nameOf(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static BlockState apply(BlockState state, String name, String value) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
        if (property == null) {
            return state;
        }
        return withValue(state, property, value);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState withValue(BlockState state, Property<?> property, String value) {
        Property<T> typed = (Property<T>) property;
        return typed.getValue(value).map(found -> state.setValue(typed, found)).orElse(state);
    }
}
