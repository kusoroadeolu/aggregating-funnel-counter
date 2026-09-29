package io.github.kusoroadeolu.afc;

public final class MathUtils {

    //splitmix 64 finalizer
    static int splitMixIndex(long numCells) {
        long id = Thread.currentThread().threadId();
        long h = id + 0x9E3779B97F4A7C15L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h =  h ^ (h >>> 31);
        return (int) (h & (numCells - 1));
    }


    /*
    jump-hash
    see https://dgryski.medium.com/consistent-hashing-algorithmic-tradeoffs-ef6b8e2fcae8 for explanation
    */
    static int jumpIndex(long numCells) {
        long id = Thread.currentThread().threadId();
        long b = -1;
        long j = 0;

        while (j < numCells) {
            b = j;
            id = id * 2862933555777941757L + 1;
            j = (long) ((b + 1) * ((double) (1L << 31) / (double) ((id >>> 33) + 1)));
        }

        return (int) b;
    }

    static int roundToPowerOfTwo(final int value) {
        return Math.max(2, 1 << (32 - Integer.numberOfLeadingZeros(value - 1)));
    }

}
