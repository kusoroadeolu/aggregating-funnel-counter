package io.github.kusoroadeolu.afc.jmh;

import io.github.kusoroadeolu.afc.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.JavaFlightRecorderProfiler;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

import static io.github.kusoroadeolu.afc.jmh.JvmArgs.*;

@Fork(value = 2, jvmArgs = {I_HEAP_ARG, M_HEAP_ARG, GC_TYPE_ARG})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
public class CounterScalingBench {

    @Param({"AFCounter", "FAACounter"})
    private String implementation;


    private LongCounter counter;

    @Setup(Level.Trial)
    public void setup() {
        counter = switch (implementation) {
            case "JCToolsCounter" -> new JCToolsLongCounter();
            case "JDKCounter" -> new JDKLongCounter();
            case "AFCounter" -> new AggregatingFunnelLongCounter();
            case "FAACounter" -> new FAACounter();
            default -> throw new IllegalArgumentException();
        };

    }

    @State(Scope.Thread)
    public static class LocalState {
        @Param({"0"})
        //        @Param({"0", "50", "80"})
        public int sumRatio;

        private static final int SEED = 52;
        private static final int SUMS_SIZE = 1024;
        private static final int MASK = SUMS_SIZE - 1;

        private static final int WRITE_OP_SIZE = 128;
        private static final int WRITE_OP_MASK = WRITE_OP_SIZE - 1;

        private boolean[] sums;
        private boolean[] writeOperation;

        private long cursor;

        @Setup(Level.Trial)
        public void initSums() {
            sums = new boolean[SUMS_SIZE];
            writeOperation = new boolean[WRITE_OP_SIZE];
        }

        @Setup(Level.Iteration)
        public void setup() {
            SplittableRandom random = new SplittableRandom(SEED);

            for (int i = 0; i < SUMS_SIZE; ++i) {
                sums[i] = (random.nextInt(0, 100) < sumRatio);
            }

            for (int i = 0; i < WRITE_OP_SIZE; ++i) {
                writeOperation[i] = (random.nextInt(0, 100) < 50);
            }
        }

        public boolean increment() {
           return writeOperation[(int) (cursor & WRITE_OP_MASK)];
        }

        public boolean shouldSum() {
            boolean s = sums[(int) (cursor & MASK)];
            cursor++;
            return s;
        }
    }

    @Benchmark
    @Threads(2)
    public void twoThread(Blackhole bh, LocalState state) {
        doWork(bh, counter, state);
    }

    @Benchmark
    @Threads(4)
    public void fourThread(Blackhole bh, LocalState state) {
        doWork(bh, counter, state);
    }

    @Benchmark
    @Threads(6)
    public void sixThread(Blackhole bh, LocalState state) {
        doWork(bh, counter, state);
    }

    @Benchmark
    @Threads(8)
    public void eightThread(Blackhole bh, LocalState state) {
        doWork(bh, counter, state);
    }

    void doWork(Blackhole bh, LongCounter counter, LocalState state) {
        boolean sum = state.shouldSum();
        if (sum) bh.consume(counter.sum());
        else {
            boolean inc = state.increment();
            if (inc) bh.consume(increment(counter));
            else bh.consume(decrement(counter));
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int increment(LongCounter counter) {
        counter.increment();
        return TOKEN;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int decrement(LongCounter counter) {
        counter.decrement();
        return TOKEN;
    }


    private static final int TOKEN = 1;

    static class BenchRunner {
        static void main() throws RunnerException {
            Options options = new OptionsBuilder()
                    .include(CounterScalingBench.class.getSimpleName())
                    .addProfiler(JavaFlightRecorderProfiler.class, "dir=C:\\jfr-mpmc-pq")
                    .build();
            new org.openjdk.jmh.runner.Runner(options).run();

        }
    }
}

/*
╭ io.github.kusoroadeolu.afc.jmh.CounterScalingBench.eightThread ─╮
│  Implementation SumRatio Score  Error   Unit                    │
│  -------------- -------- ------ ------- ------                  │
│  AFCounter      0        46.015 ± 2.391 ops/us                  │
│  FAACounter     0        33.103 ± 1.789 ops/us                  │
╰─────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.afc.jmh.CounterScalingBench.fourThread ─╮
│  Implementation SumRatio Score  Error   Unit                   │
│  -------------- -------- ------ ------- ------                 │
│  AFCounter      0        25.268 ± 0.415 ops/us                 │
│  FAACounter     0        24.647 ± 1.335 ops/us                 │
╰────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.afc.jmh.CounterScalingBench.sixThread ─╮
│  Implementation SumRatio Score  Error   Unit                  │
│  -------------- -------- ------ ------- ------                │
│  AFCounter      0        40.112 ± 0.862 ops/us                │
│  FAACounter     0        27.757 ± 1.883 ops/us                │
╰───────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.afc.jmh.CounterScalingBench.twoThread ─╮
│  Implementation SumRatio Score  Error   Unit                  │
│  -------------- -------- ------ ------- ------                │
│  AFCounter      0        20.375 ± 1.285 ops/us                │
│  FAACounter     0        27.843 ± 7.893 ops/us                │
╰───────────────────────────────────────────────────────────────╯

* */

/*
* ╭ io.github.kusoroadeolu.afc.jmh.CounterScalingBench.eightThread ─╮
│  Implementation SumRatio Score   Error    Unit                  │
│  -------------- -------- ------- -------- ------                │
│  JCToolsCounter 0        567.481 ± 16.703 ops/us                │
│  JCToolsCounter 50       117.944 ± 3.116  ops/us                │
│  JCToolsCounter 80       177.775 ± 4.403  ops/us                │
│  JDKCounter     0        520.141 ± 41.459 ops/us                │
│  JDKCounter     50       125.918 ± 2.784  ops/us                │
│  JDKCounter     80       187.113 ± 5.236  ops/us                │
╰─────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.afc.jmh.CounterScalingBench.fourThread ─╮
│  Implementation SumRatio Score   Error    Unit                 │
│  -------------- -------- ------- -------- ------               │
│  JCToolsCounter 0        407.869 ± 13.881 ops/us               │
│  JCToolsCounter 50       61.526  ± 2.304  ops/us               │
│  JCToolsCounter 80       80.643  ± 15.644 ops/us               │
│  JDKCounter     0        405.981 ± 4.914  ops/us               │
│  JDKCounter     50       89.315  ± 2.309  ops/us               │
│  JDKCounter     80       152.786 ± 3.265  ops/us               │
╰────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.afc.jmh.CounterScalingBench.oneThread ─╮
│  Implementation SumRatio Score   Error    Unit                │
│  -------------- -------- ------- -------- ------              │
│  JCToolsCounter 0        131.838 ± 4.541  ops/us              │
│  JCToolsCounter 50       124.433 ± 8.748  ops/us              │
│  JCToolsCounter 80       132.819 ± 5.028  ops/us              │
│  JDKCounter     0        127.978 ± 5.133  ops/us              │
│  JDKCounter     50       190.887 ± 6.162  ops/us              │
│  JDKCounter     80       292.004 ± 12.570 ops/us              │
╰───────────────────────────────────────────────────────────────╯
* */

