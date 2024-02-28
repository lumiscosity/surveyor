package folk.sisby.surveyor.util;

import net.minecraft.nbt.NbtCompound;

import java.util.stream.IntStream;

public record ByteArrayUInts(byte[] value) implements UIntArray {
    @Override
    public int[] getUncompressed() {
        return IntStream.range(0, value.length).map(i -> value[i] + UINT_BYTE_OFFSET).toArray();
    }

    @Override
    public int[] getUnmasked(UIntArray mask) {
        int[] unmasked = new int[256];
        int empty = 0;
        for (int i = 0; i < 256; i++) {
            unmasked[i] = value[i - empty] + UINT_BYTE_OFFSET;
            if (mask.isEmpty(i)) empty++;
        }
        return unmasked;
    }

    @Override
    public void writeNbt(NbtCompound nbt, String key) {
        nbt.putByteArray(key, value);
    }

    @Override
    public boolean isEmpty(int i) {
        return value[i] == -128;
    }

    @Override
    public int get(int i) {
        return value[i] + UINT_BYTE_OFFSET;
    }
}
