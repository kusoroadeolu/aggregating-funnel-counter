package io.github.kusoroadeolu.afc;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import static io.github.kusoroadeolu.afc.MathUtils.roundToPowerOfTwo;

/*
* Based on the paper https://arxiv.org/pdf/2411.14420
*
* A scalable & linearizable fetch and add implementation. Unlike
* the cpu instruction, this implementation is blocking & not wait free
*
* The core idea of this implementation is to spread contention on a fetch and add instruction across multiple memory locations
* Aggregators
*
* To improve locality while spreading contention across we use a uniformly spread hash which uses thread ids.
* However, since collisions on indexes can still occur, we combine incoming requests into a batch, allowing only one thread to observe and
* possibly modify the aggregator below the current one or the base long.
*
* This class also includes false sharing protection for different classes and fields.
*
* This counter is much more similar to a single fetch and add counter than a
* striped counter and can be used to build scalable striped counters. This class does contain some extra machinery to
* ensure a thread always returns a linearizable value (similar to how faa works) at the cost of some thrpt.
*
* To adapt the batch list for gc environments, rather than using a stack and keeping the latest batch as the head of the stack (keeping unreachable refs alive),
* we instead use a queue where the tail is the latest batch, therefore, memory scales with the slowest thread holding a reference to an older batch rather than the
* length of the batch
*
* If you don't need a return value, you can check out the other implementation
* which doesn't include the batch machinery and provides a thrpt advantage and no gc inference
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

public class AggregatingXadd extends BasePad implements AtomicLongCounter {
    private static final int NCPU = Runtime.getRuntime().availableProcessors();
    private static final int FUNNEL_DEPTH = (Math.max(1, MathUtils.roundToPowerOfTwo(NCPU) / 16)); //a funnel depth of one seems to be the best for my cpu count
    // I wonder if we can make this adaptive for computers with more cpus, maybe something like (roundToPow2(NCPU) >>> 1)?
    // Hmm, though that'd only be true if higher depth alleviates contention past 8 threads which unfortunately I cannot prove yet


    private final AggregatorArray[] aggregators;
    private final boolean unsigned;

    public AggregatingXadd(boolean isUnsigned) {
        this.aggregators = new AggregatorArray[FUNNEL_DEPTH];
        for (int i = 0; i < FUNNEL_DEPTH; ++i) {
            int pow = i + 1;
            int size = roundToPowerOfTwo(NCPU / (1 << pow));
            aggregators[i] = new AggregatorArray(size);
        }

        this.unsigned = isUnsigned;
    }

    //for lincheck tests
    AggregatingXadd(int arraySize) {
        this.aggregators = new AggregatorArray[arraySize];
        aggregators[0] = new AggregatorArray(arraySize);
        unsigned = true;
    }

    public AggregatingXadd() {
        this(true);
    }

    public long fetchAndIncrement() {
       return increment(1);
    }

    public long fetchAndDecrement() {
       if (unsigned) throw new IllegalArgumentException("Cannot decrement an unsigned aggregating xadd");
       return increment(-1);
    }

    public long get() {
        return base;
    }

    long increment(long by) {
        if (by == 0) return get();
        return atomicAdd(aggregators, Math.absExact(by), 0,by < 0);
    }

    public boolean compareAndSet(long from, long to) {
        if (unsigned && to < 0) throw new IllegalArgumentException("Cannot decrement an unsigned aggregating xadd");
        return BASE.compareAndSet(this, from, to);
    }

    long atomicAdd(AggregatorArray[] aggregators, long incrementBy, int level, boolean isNegative){
        var aggregatorArray = aggregators[level];
        long size = aggregatorArray.size();

        //account for the fact we split the array into negative and positive sides

        int normalizedIndex;
        if (unsigned) normalizedIndex = MathUtils.jumpIndex(size);
        else {
            //account for the fact we split the array into negative and positive sides
            long half = size >>> 1;
            int index = MathUtils.jumpIndex(half);
            normalizedIndex = (int) (isNegative ? index + half : index);
        }


        var aggregator = aggregatorArray.indexAt(normalizedIndex);

        Batch start = aggregator.latestBatch();
        long aBefore = aggregator.fetchAndAddValue(incrementBy);

        Batch latest;
        while ((latest = aggregator.latestBatch()).after < aBefore) {
            Thread.onSpinWait();
        }

        VarHandle.acquireFence(); //avoid reordering downwards
        //also allows the writing thread to synchronize-with the latest write to value

        if (aBefore == latest.after) {
            long value = aggregator.value; //plain read piggy-backed by acquire fence
            long diff = value - aBefore;
            long mainBefore;

            if (level == (FUNNEL_DEPTH - 1)) mainBefore = (long) BASE.getAndAdd(this, isNegative ? -diff : diff);
            else mainBefore = atomicAdd(aggregators, diff, level + 1, isNegative);

            Batch newBatch = new Batch(aBefore, value, mainBefore);
            latest.next = newBatch;
            LATEST.setRelease(aggregator, newBatch);
            return mainBefore;

        } else {
            var b = start;
            while (!(b.before <= aBefore && aBefore < b.after)) b = b.next;
            if (isNegative) return (b.mainBefore + b.before) - aBefore;
            else return b.mainBefore + (aBefore - b.before);
        }
    }

    //Wrapper class to make working with indexes less confusing
    static class AggregatorArray {
        final Aggregator[] aggregators;
        final long size;

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

        public long size() {
            return size;
        }

    }



    @SuppressWarnings("unused")
    static class AggregatorPad0 {
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

    static class AggregatorLatestField extends AggregatorPad0 {
        volatile Batch latest = new Batch(0, 0, 0);

        public Batch latestBatch() {
            return (Batch) LATEST.getOpaque(this);
        }
    }

    @SuppressWarnings("unused")
    static class AggregatorPad1 extends AggregatorLatestField {
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

    static class AggregatorValueField extends AggregatorPad1 {
        long value;


        long fetchAndAddValue(long by) {
            return (long) VALUE.getAndAdd(this, by);
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

    static class Batch {
        final long before;
        final long after;
        final long mainBefore;
        Batch next; //piggybacked by write to agg#latestBatch

        public Batch(long before, long after, long mainBefore) {
            this.before = before;
            this.after = after;
            this.mainBefore = mainBefore;
        }
    }


    private static final VarHandle BASE;
    private static final VarHandle VALUE;
    private static final VarHandle LATEST;

    static {
        var l = MethodHandles.lookup();
        try {
            BASE = l.findVarHandle(AggregatingXadd.class, "base", long.class);
            VALUE = l.findVarHandle(Aggregator.class, "value", long.class);
            LATEST = l.findVarHandle(Aggregator.class, "latest", Batch.class);
        }catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}

