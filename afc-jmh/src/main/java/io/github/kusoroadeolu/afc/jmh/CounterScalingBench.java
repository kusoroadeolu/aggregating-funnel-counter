package io.github.kusoroadeolu.afc.jmh;

import io.github.kusoroadeolu.afc.AggregatingFunnelLongCounter;
import io.github.kusoroadeolu.afc.AtomicLongCounter;
import io.github.kusoroadeolu.afc.FetchAndAddLongCounter;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

import static io.github.kusoroadeolu.afc.jmh.JvmArgs.*;

@Fork(value = 3, jvmArgs = {I_HEAP_ARG, M_HEAP_ARG, GC_TYPE_ARG})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
public class CounterScalingBench {

    @Param({"AggregatingFunnelCounter", "FetchAndAddCounter"})
    private String implementation;


    private AtomicLongCounter counter;

    @Setup(Level.Trial)
    public void setup() {
        counter = switch (implementation) {
            case "AggregatingFunnelCounter" -> new AggregatingFunnelLongCounter();
            case "FetchAndAddCounter" -> new FetchAndAddLongCounter();
            default -> throw new IllegalArgumentException();
        };

    }

    @State(Scope.Thread)
    public static class LocalState {
        private static final int SEED = 52;

        private static final int WRITE_OP_SIZE = 128;
        private static final int WRITE_OP_MASK = WRITE_OP_SIZE - 1;

        private boolean[] writeOperation;

        private long cursor;

        @Setup(Level.Trial)
        public void initSums() {
            writeOperation = new boolean[WRITE_OP_SIZE];
        }

        @Setup(Level.Iteration)
        public void setup() {
            SplittableRandom random = new SplittableRandom(SEED);

            for (int i = 0; i < WRITE_OP_SIZE; ++i) {
                writeOperation[i] = (random.nextInt(0, 100) < 50);
            }
        }

        public boolean increment() {
           return writeOperation[(int) (cursor & WRITE_OP_MASK)];
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
    @Threads(8)
    public void eightThread(Blackhole bh, LocalState state) {
        doWork(bh, counter, state);
    }

    void doWork(Blackhole bh, AtomicLongCounter counter, LocalState state) {
        boolean inc = state.increment();
        if (inc) bh.consume(increment(counter));
        else bh.consume(decrement(counter));
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int increment(AtomicLongCounter counter) {
        counter.increment();
        return TOKEN;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int decrement(AtomicLongCounter counter) {
        counter.decrement();
        return TOKEN;
    }


    private static final int TOKEN = 1;

    static class BenchRunner {
        static void main() throws RunnerException {
            Options options = new OptionsBuilder()
                    .include(CounterScalingBench.class.getSimpleName())
                    .build();
            new org.openjdk.jmh.runner.Runner(options).run();

        }
    }
}

/*
╭ io.github.kusoroadeolu.afc.jmh.CounterScalingBench.eightThread ─╮
│  Implementation           Score  Error   Unit                   │
│  ------------------------ ------ ------- ------                 │
│  AggregatingFunnelCounter 47.522 ± 1.813 ops/us                 │
│  FetchAndAddCounter       31.857 ± 1.668 ops/us                 │
╰─────────────────────────────────────────────────────────────────

╭ io.github.kusoroadeolu.afc.jmh.CounterScalingBench.fourThread ─╮
│  Implementation           Score  Error   Unit                  │
│  ------------------------ ------ ------- ------                │
│  AggregatingFunnelCounter 26.694 ± 0.706 ops/us                │
│  FetchAndAddCounter       21.608 ± 0.764 ops/us                │
╰────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.afc.jmh.CounterScalingBench.twoThread ─╮
│  Implementation           Score  Error   Unit                 │
│  ------------------------ ------ ------- ------               │
│  AggregatingFunnelCounter 24.965 ± 0.646 ops/us               │
│  FetchAndAddCounter       31.147 ± 2.809 ops/us               │
╰───────────────────────────────────────────────────────────────╯
* */


