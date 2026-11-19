package com.homestead.mover.snapshot;

/**
 * Long-array bit packing, cross-long aware. Bits per entry = max(2, ceil(log2(paletteSize))).
 * Index order matches the snapshot: {@code index = x + z * sizeX + y * sizeX * sizeZ}.
 */
public final class PackedStates {
    private final long[] data;
    private final int bitsPerEntry;
    private final int size;
    private final long mask;

    public PackedStates(long[] data, int bitsPerEntry, int size) {
        if (bitsPerEntry < 1 || bitsPerEntry > 32) {
            throw new IllegalArgumentException("bitsPerEntry out of range: " + bitsPerEntry);
        }
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative: " + size);
        }
        int needed = wordsFor(size, bitsPerEntry);
        if (data.length < needed) {
            throw new IllegalArgumentException("data too short: have " + data.length + ", need " + needed);
        }
        this.data = new long[needed];
        System.arraycopy(data, 0, this.data, 0, needed);
        this.bitsPerEntry = bitsPerEntry;
        this.mask = (1L << bitsPerEntry) - 1L;
        this.size = size;
    }

    public static PackedStates empty(int size, int paletteSize) {
        int bits = bitsForPaletteSize(paletteSize);
        return new PackedStates(new long[wordsFor(size, bits)], bits, size);
    }

    public static int bitsForPaletteSize(int paletteSize) {
        if (paletteSize <= 0) {
            throw new IllegalArgumentException("paletteSize must be > 0");
        }
        int bits = 32 - Integer.numberOfLeadingZeros(paletteSize - 1);
        return Math.max(2, bits);
    }

    public static int wordsFor(int size, int bitsPerEntry) {
        return (int) (((long) size * bitsPerEntry + 63L) >> 6);
    }

    public int size() {
        return size;
    }

    public int bitsPerEntry() {
        return bitsPerEntry;
    }

    public long[] copyData() {
        return data.clone();
    }

    public int get(int index) {
        checkIndex(index);
        long startBit = (long) index * bitsPerEntry;
        int startLong = (int) (startBit >>> 6);
        int bitOffset = (int) (startBit & 63L);
        if (bitOffset + bitsPerEntry <= 64) {
            return (int) ((data[startLong] >>> bitOffset) & mask);
        }
        int endBits = bitOffset + bitsPerEntry - 64;
        long low = data[startLong] >>> bitOffset;
        long high = data[startLong + 1] & ((1L << endBits) - 1L);
        return (int) ((low | (high << (64 - bitOffset))) & mask);
    }

    public void set(int index, int value) {
        checkIndex(index);
        if (value < 0 || ((long) value & ~mask) != 0L) {
            throw new IllegalArgumentException("value out of range for " + bitsPerEntry + " bits: " + value);
        }
        long startBit = (long) index * bitsPerEntry;
        int startLong = (int) (startBit >>> 6);
        int bitOffset = (int) (startBit & 63L);
        if (bitOffset + bitsPerEntry <= 64) {
            long cleared = data[startLong] & ~(mask << bitOffset);
            data[startLong] = cleared | (((long) value & mask) << bitOffset);
            return;
        }
        int lowBits = 64 - bitOffset;
        long lowMask = (1L << lowBits) - 1L;
        data[startLong] = (data[startLong] & ~(lowMask << bitOffset)) | (((long) value & lowMask) << bitOffset);
        int highBits = bitsPerEntry - lowBits;
        long highMask = (1L << highBits) - 1L;
        data[startLong + 1] = (data[startLong + 1] & ~highMask) | (((long) value >>> lowBits) & highMask);
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("index=" + index + " size=" + size);
        }
    }
}
