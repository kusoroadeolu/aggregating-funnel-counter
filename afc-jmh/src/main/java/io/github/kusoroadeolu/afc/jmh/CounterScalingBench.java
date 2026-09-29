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

    @Param({"AggregatingXaddCounter", "XaddCounter" ,"AggregatingAtomicCounter"})
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
│  AggregatingXaddCounter   46.319 ± 1.192 ops/us                 │
│  XaddCounter              32.147 ± 0.364 ops/us                 │
│  AggregatingAtomicCounter 61.651 ± 2.399 ops/us                 │
╰─────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.afc.jmh.CounterScalingBench.fourThread ─╮
│  Implementation           Score  Error   Unit                  │
│  ------------------------ ------ ------- ------                │
│  AggregatingXaddCounter   27.223 ± 1.382 ops/us                │
│  XaddCounter              25.718 ± 0.629 ops/us                │
│  AggregatingAtomicCounter 35.448 ± 0.765 ops/us                │
╰────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.afc.jmh.CounterScalingBench.twoThread ─╮
│  Implementation           Score  Error   Unit                 │
│  ------------------------ ------ ------- ------               │
│  AggregatingXaddCounter   19.862 ± 0.467 ops/us               │
│  XaddCounter              28.261 ± 2.153 ops/us               │
│  AggregatingAtomicCounter 24.361 ± 1.292 ops/us               │
╰───────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.afc.jmh.CounterScalingBench.eightThread ─╮
│  Implementation           Score  Error   Unit                   │
│  ------------------------ ------ ------- ------                 │
│  AggregatingXaddCounter   45.739 ± 1.744 ops/us                 │
│  XaddCounter              37.299 ± 1.580 ops/us                 │
│  AggregatingAtomicCounter 62.102 ± 2.598 ops/us                 │
╰─────────────────────────────────────────────────────────────────╯
* */

/* FUNNEL-DEPTH = 2
╭ io.github.kusoroadeolu.afc.jmh.CounterScalingBench.eightThread ─╮
│  Implementation           Score  Error   Unit                   │
│  ------------------------ ------ ------- ------                 │
│  AggregatingXaddCounter   26.930 ± 1.233 ops/us                 │
│  AggregatingAtomicCounter 37.144 ± 1.237 ops/us                 │
╰─────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.afc.jmh.CounterScalingBench.fourThread ─╮
│  Implementation           Score  Error   Unit                  │
│  ------------------------ ------ ------- ------                │
│  AggregatingXaddCounter   12.366 ± 0.286 ops/us                │
│  AggregatingAtomicCounter 19.087 ± 0.239 ops/us                │
╰────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.afc.jmh.CounterScalingBench.twoThread ─╮
│  Implementation           Score  Error   Unit                 │
│  ------------------------ ------ ------- ------               │
│  AggregatingXaddCounter   11.998 ± 0.543 ops/us               │
│  AggregatingAtomicCounter 21.401 ± 0.704 ops/us               │
╰───────────────────────────────────────────────────────────────╯
* */


