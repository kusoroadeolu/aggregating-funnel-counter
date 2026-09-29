package io.github.kusoroadeolu.afc.jmh;

import io.github.kusoroadeolu.afc.AggregatingAtomicCounter;
import io.github.kusoroadeolu.afc.AggregatingXaddCounter;
import io.github.kusoroadeolu.afc.AtomicLongCounter;
import io.github.kusoroadeolu.afc.XaddCounter;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.JavaFlightRecorderProfiler;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

import static io.github.kusoroadeolu.afc.jmh.JvmArgs.*;

@Fork(value = 2, jvmArgs = {I_HEAP_ARG, M_HEAP_ARG, GC_TYPE_ARG})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@BenchmarkMode(Mode.Throughput)
public class CounterScalingBench {

    @Param({"AggregatingXaddCounter","AggregatingAtomicCounter"})
    private String implementation;

    static boolean unsigned = true;

    private AtomicLongCounter counter;

    @Setup(Level.Trial)
    public void setup() {
        counter = switch (implementation) {
            case "AggregatingXaddCounter" -> new AggregatingXaddCounter(unsigned);
            case "XaddCounter" -> new XaddCounter();
            case "AggregatingAtomicCounter" -> new AggregatingAtomicCounter(unsigned);
            default -> throw new IllegalArgumentException();
        };

    }

    @Benchmark
    @Threads(2)
    public void twoThread(Blackhole bh) {
        doWork(bh, counter);
    }

    @Benchmark
    @Threads(4)
    public void fourThread(Blackhole bh) {
        doWork(bh, counter);
    }

    @Benchmark
    @Threads(8)
    public void eightThread(Blackhole bh) {
        doWork(bh, counter);
    }

    void doWork(Blackhole bh, AtomicLongCounter counter) {
        bh.consume(counter.fetchAndIncrement());
    }

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

/* FUNNEL-DEPTH = 1
╭ io.github.kusoroadeolu.afc.jmh.CounterScalingBench.eightThread ─╮
│  Implementation           Score  Error   Unit                   │
│  ------------------------ ------ ------- ------                 │
│  AggregatingXaddCounter   48.150 ± 1.982 ops/us                 │
│  XaddCounter              32.147 ± 0.364 ops/us                 │
│  AggregatingAtomicCounter 58.389 ± 2.093 ops/us                 │
╰─────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.afc.jmh.CounterScalingBench.fourThread ─╮
│  Implementation           Score  Error   Unit                  │
│  ------------------------ ------ ------- ------                │
│  AggregatingXaddCounter   33.071 ± 0.797 ops/us                │
│  XaddCounter              25.718 ± 0.629 ops/us                │
│  AggregatingAtomicCounter 48.515 ± 1.865 ops/us                │
╰────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.afc.jmh.CounterScalingBench.twoThread ─╮
│  Implementation           Score  Error   Unit                 │
│  ------------------------ ------ ------- ------               │
│  AggregatingXaddCounter   44.089 ± 1.295 ops/us               │
│  XaddCounter              28.261 ± 2.153 ops/us               │
│  AggregatingAtomicCounter 49.729 ± 2.910 ops/us               │
╰───────────────────────────────────────────────────────────────╯
* */

/* FUNNEL-DEPTH = 2
╭ io.github.kusoroadeolu.afc.jmh.CounterScalingBench.eightThread ─╮
│  Implementation           Score  Error   Unit                   │
│  ------------------------ ------ ------- ------                 │
│  AggregatingXaddCounter   20.618 ± 2.411 ops/us                 │
│  AggregatingAtomicCounter 36.239 ± 1.292 ops/us                 │
╰─────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.afc.jmh.CounterScalingBench.fourThread ─╮
│  Implementation           Score  Error   Unit                  │
│  ------------------------ ------ ------- ------                │
│  AggregatingXaddCounter   17.965 ± 0.848 ops/us                │
│  AggregatingAtomicCounter 26.564 ± 0.480 ops/us                │
╰────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.afc.jmh.CounterScalingBench.twoThread ─╮
│  Implementation           Score  Error   Unit                 │
│  ------------------------ ------ ------- ------               │
│  AggregatingXaddCounter   12.759 ± 0.702 ops/us               │
│  AggregatingAtomicCounter 19.409 ± 0.201 ops/us               │
╰───────────────────────────────────────────────────────────────╯
* */


