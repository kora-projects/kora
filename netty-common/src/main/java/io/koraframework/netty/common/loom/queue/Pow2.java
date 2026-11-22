package io.koraframework.netty.common.loom.queue;

public final class Pow2 {
    public static final int MAX_POW2 = 1 << 30;

    public static int roundToPowerOfTwo(final int value) {
        if (value > MAX_POW2) {
            throw new IllegalArgumentException("There is no larger power of 2 int for value:" + value + " since it exceeds 2^31.");
        }
        if (value < 0) {
            throw new IllegalArgumentException("Given value:" + value + ". Expecting value >= 0.");
        }
        final int nextPow2 = 1 << (32 - Integer.numberOfLeadingZeros(value - 1));
        return nextPow2;
    }

    public static boolean isPowerOfTwo(final int value) {
        return (value & (value - 1)) == 0;
    }

    public static long align(final long value, final int alignment) {
        if (!isPowerOfTwo(alignment)) {
            throw new IllegalArgumentException("alignment must be a power of 2:" + alignment);
        }
        return (value + (alignment - 1)) & ~(alignment - 1);
    }
}
