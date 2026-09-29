package io.github.kusoroadeolu.afc;

public final class MathUtils {

    //splitmix 64 finalizer
    static long rand() {
        long id = Thread.currentThread().threadId();
        long h = id + 0x9E3779B97F4A7C15L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h =  h ^ (h >>> 31);
        return h;
    }

    static int roundToPowerOfTwo(final int value) {
        return Math.max(2, 1 << (32 - Integer.numberOfLeadingZeros(value - 1)));
    }

}
