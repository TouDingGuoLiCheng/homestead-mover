package com.homestead.mover.snapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Palette of distinct block states. Capture appends; decode restores a known order. */
public final class Palette {
    private final List<RawBlockState> states;
    private final Map<RawBlockState, Integer> index;

    private Palette(List<RawBlockState> states, Map<RawBlockState, Integer> index) {
        this.states = states;
        this.index = index;
    }

    public static Palette empty() {
        return new Palette(new ArrayList<>(), new HashMap<>());
    }

    /** Restores a palette in file order; duplicate states are rejected. */
    public static Palette ofOrdered(List<RawBlockState> ordered) {
        List<RawBlockState> copy = new ArrayList<>(ordered.size());
        Map<RawBlockState, Integer> lookup = new HashMap<>();
        for (RawBlockState state : ordered) {
            if (lookup.put(state, copy.size()) != null) {
                throw new IllegalArgumentException("duplicate palette entry: " + state);
            }
            copy.add(state);
        }
        return new Palette(copy, lookup);
    }

    /** Index of the state, appending it when unseen. Capture-side operation. */
    public int indexOf(RawBlockState state) {
        Integer existing = index.get(state);
        if (existing != null) {
            return existing;
        }
        int next = states.size();
        states.add(state);
        index.put(state, next);
        return next;
    }

    public int size() {
        return states.size();
    }

    public RawBlockState stateAt(int paletteIndex) {
        if (paletteIndex < 0 || paletteIndex >= states.size()) {
            throw new IndexOutOfBoundsException("palette index=" + paletteIndex + " size=" + states.size());
        }
        return states.get(paletteIndex);
    }

    public Optional<Integer> indexByName(String name) {
        for (int i = 0; i < states.size(); i++) {
            if (states.get(i).name().equals(name)) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    public List<RawBlockState> states() {
        return Collections.unmodifiableList(states);
    }
}
