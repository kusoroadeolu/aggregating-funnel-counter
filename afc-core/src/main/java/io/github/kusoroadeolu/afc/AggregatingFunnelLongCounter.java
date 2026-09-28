package io.github.kusoroadeolu.afc;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/*
* Based on the paper https://arxiv.org/pdf/2411.14420*
*
* A blocking but scalable & linearizable fetch and add implementation
* 
*  This counter is much more similar to a single fetch and add counter than a
*  striped counter and can be used to build scalable striped counters.
*  This class provides around a 55% thrpt improvement over fetch and add at 8 threads
*  though at the cost of significantly more memory usage
* */
class BaseFieldLPad {
    byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b
    byte b010,b011,b012,b013,b014,b015,b016,b017;// 16b
    byte b020,b021,b022,b023,b024,b025,b026,b027;// 24b
    byte b030,b031,b032,b033,b034,b035,b036,b037;// 32b
    byte b040,b041,b042,b043,b044,b045,b046,b047;// 40b
    byte b050,b051,b052,b053,b054,b055,b056,b057;// 48b
    byte b060,b061,b062,b063,b064,b065,b066,b067;// 56b
    byte b070,b071,b072,b073,b074,b075,b076,b077;// 64b
    byte b100,b101,b102,b103,b104,b105,b106,b107;// 72b
    byte b110,b111,b112,b113,b114,b115,b116,b117;// 80b
    byte b120,b121,b122,b123,b124,b125,b126,b127;// 88b
    byte b130,b131,b132,b133,b134,b135,b136,b137;// 96b
    byte b140,b141,b142,b143,b144,b145,b146,b147;//104b
    byte b150,b151,b152,b153,b154,b155,b156,b157;//112b
    byte b160,b161,b162,b163,b164,b165,b166,b167;//120b
}

class BaseField extends BaseFieldLPad {
    volatile long base;
}

class BasePad extends BaseField {
    byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b
    byte b010,b011,b012,b013,b014,b015,b016,b017;// 16b
    byte b020,b021,b022,b023,b024,b025,b026,b027;// 24b
    byte b030,b031,b032,b033,b034,b035,b036,b037;// 32b
    byte b040,b041,b042,b043,b044,b045,b046,b047;// 40b
    byte b050,b051,b052,b053,b054,b055,b056,b057;// 48b
    byte b060,b061,b062,b063,b064,b065,b066,b067;// 56b
    byte b070,b071,b072,b073,b074,b075,b076,b077;// 64b
    byte b100,b101,b102,b103,b104,b105,b106,b107;// 72b
    byte b110,b111,b112,b113,b114,b115,b116,b117;// 80b
    byte b120,b121,b122,b123,b124,b125,b126,b127;// 88b
    byte b130,b131,b132,b133,b134,b135,b136,b137;// 96b
    byte b140,b141,b142,b143,b144,b145,b146,b147;//104b
    byte b150,b151,b152,b153,b154,b155,b156,b157;//112b
    byte b160,b161,b162,b163,b164,b165,b166,b167;//120b
    byte b170,b171,b172,b173,b174,b175,b176,b177;//128b

}


public class AggregatingFunnelLongCounter extends BasePad implements LongCounter {
    private static final int NCPU = Runtime.getRuntime().availableProcessors();
    private static final int FUNNEL_DEPTH = 1; //a funnel depth of one seems to be the best for my cpu count
    // I wonder if we can make this adaptive for computers with more cpus, maybe something like (roundToPow2(NCPU) >>> 1)?
    // Hmm, though that'd only be true if higher depth alleviates contention past 8 threads which unfortunately I cannot prove yet


    private final AggregatorArray[] aggregators;

    public AggregatingFunnelLongCounter() {
        this.aggregators = new AggregatorArray[FUNNEL_DEPTH];
        for (int i = 0; i < FUNNEL_DEPTH; ++i) {
            int pow = i + 1;
            int size = roundToPowerOfTwo(NCPU / (1 << pow));
            aggregators[i] = new AggregatorArray(size);
        }
    }

    @Override
    public void increment() {
        increment(1);
    }

    @Override
    public void decrement() {
        increment(-1);
    }

    @Override
    public long sum() {
        return base;
    }

    public void increment(int by) {
        if (by == 0) return;

        long id = Thread.currentThread().threadId();
        atomicAdd(aggregators, Math.absExact(by), 0, id, by < 0);
    }

    void atomicAdd(AggregatorArray[] aggregators, long incrementBy, int level, long id, boolean isNegative){
        var aggregatorArray = aggregators[level];

        //account for the fact we split the array into negative and positive sides
        int half = aggregatorArray.size() >>> 1;
        int mask = half - 1;

        int index = index(id, mask);

        int normalizedIndex = isNegative ? index + half : index;
        var aggregator = aggregatorArray.indexAt(normalizedIndex);


        long aBefore = aggregator.fetchAndAddValue(incrementBy);
        long after;

        while ((after = aggregator.after) < aBefore) { // <- can use an acquire here
            Thread.onSpinWait();
        }

        if (aBefore == after) { //we can yield before we read value? to allow other threads make progress?
            long value = aggregator.value;
            long diff = value - aBefore;

            if (level == (FUNNEL_DEPTH - 1)) BASE.getAndAdd(this, isNegative ? -diff : diff);
            else atomicAdd(aggregators, diff, level + 1, id, isNegative);

            aggregator.after = value; // <- can use a release here
        }
    }

    private int index(long id, int mask) {
        long h = id + 0x9E3779B97F4A7C15L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h =  h ^ (h >>> 31);
        return (int) (h & mask);
    }

    //Wrapper class to make working with indexes less confusing
    static class AggregatorArray {
        final Aggregator[] aggregators;
        final int size;

        public AggregatorArray(int size) {
            aggregators = new Aggregator[size];
            for (int i = 0; i < size; ++i) {
                aggregators[i] = new Aggregator();
            }

            this.size = size;
        }

        public Aggregator indexAt(int index) {
            return aggregators[index];
        }

        public int size() {
            return size;
        }

    }


    static int roundToPowerOfTwo(final int value) {
        return Math.max(2, 1 << (32 - Integer.numberOfLeadingZeros(value - 1)));
    }

    @SuppressWarnings("unused")
    static class AggregatorLPad {
        byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b
        byte b010,b011,b012,b013,b014,b015,b016,b017;// 16b
        byte b020,b021,b022,b023,b024,b025,b026,b027;// 24b
        byte b030,b031,b032,b033,b034,b035,b036,b037;// 32b
        byte b040,b041,b042,b043,b044,b045,b046,b047;// 40b
        byte b050,b051,b052,b053,b054,b055,b056,b057;// 48b
        byte b060,b061,b062,b063,b064,b065,b066,b067;// 56b
        byte b070,b071,b072,b073,b074,b075,b076,b077;// 64b
        byte b100,b101,b102,b103,b104,b105,b106,b107;// 72b
        byte b110,b111,b112,b113,b114,b115,b116,b117;// 80b
        byte b120,b121,b122,b123,b124,b125,b126,b127;// 88b
        byte b130,b131,b132,b133,b134,b135,b136,b137;// 96b
        byte b140,b141,b142,b143,b144,b145,b146,b147;//104b
        byte b150,b151,b152,b153,b154,b155,b156,b157;//112b
        byte b160,b161,b162,b163,b164,b165,b166,b167;//120b
        byte b170,b171,b172,b173,b174,b175,b176,b177;//128b
    }

    static class AggregatorAfterField extends AggregatorLPad {
        volatile long after;
    }

    @SuppressWarnings("unused")
    static class AggregatorAfterFieldPad extends AggregatorAfterField {
        byte b000, b001, b002, b003, b004, b005, b006, b007;//  8b
        byte b010, b011, b012, b013, b014, b015, b016, b017;// 16b
        byte b020, b021, b022, b023, b024, b025, b026, b027;// 24b
        byte b030, b031, b032, b033, b034, b035, b036, b037;// 32b
        byte b040, b041, b042, b043, b044, b045, b046, b047;// 40b
        byte b050, b051, b052, b053, b054, b055, b056, b057;// 48b
        byte b060, b061, b062, b063, b064, b065, b066, b067;// 56b
        byte b070, b071, b072, b073, b074, b075, b076, b077;// 64b
        byte b100, b101, b102, b103, b104, b105, b106, b107;// 72b
        byte b110, b111, b112, b113, b114, b115, b116, b117;// 80b
        byte b120, b121, b122, b123, b124, b125, b126, b127;// 88b
        byte b130, b131, b132, b133, b134, b135, b136, b137;// 96b
        byte b140, b141, b142, b143, b144, b145, b146, b147;//104b
        byte b150, b151, b152, b153, b154, b155, b156, b157;//112b
        byte b160, b161, b162, b163, b164, b165, b166, b167;//120b
    }

    static class AggregatorValueField extends AggregatorAfterFieldPad {
        volatile long value;


        long fetchAndAddValue(long by) {
            return (long) VALUE.getAndAddAcquire(this, by);
        }
    }

    @SuppressWarnings("unused")
    static class Aggregator extends AggregatorValueField {
        byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b
        byte b010,b011,b012,b013,b014,b015,b016,b017;// 16b
        byte b020,b021,b022,b023,b024,b025,b026,b027;// 24b
        byte b030,b031,b032,b033,b034,b035,b036,b037;// 32b
        byte b040,b041,b042,b043,b044,b045,b046,b047;// 40b
        byte b050,b051,b052,b053,b054,b055,b056,b057;// 48b
        byte b060,b061,b062,b063,b064,b065,b066,b067;// 56b
        byte b070,b071,b072,b073,b074,b075,b076,b077;// 64b
        byte b100,b101,b102,b103,b104,b105,b106,b107;// 72b
        byte b110,b111,b112,b113,b114,b115,b116,b117;// 80b
        byte b120,b121,b122,b123,b124,b125,b126,b127;// 88b
        byte b130,b131,b132,b133,b134,b135,b136,b137;// 96b
        byte b140,b141,b142,b143,b144,b145,b146,b147;//104b
        byte b150,b151,b152,b153,b154,b155,b156,b157;//112b
        byte b160,b161,b162,b163,b164,b165,b166,b167;//120b

    }


    private static final VarHandle BASE;
    private static final VarHandle VALUE;

    static {
        var l = MethodHandles.lookup();
        try {
            BASE = l.findVarHandle(AggregatingFunnelLongCounter.class, "base", long.class);
            VALUE = l.findVarHandle(Aggregator.class, "value", long.class);
        }catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
