package io.github.kusoroadeolu.afc.jmh;

import io.github.kusoroadeolu.afc.AggregatingAtomicCounter;
import io.github.kusoroadeolu.afc.AggregatingXadd;
import io.github.kusoroadeolu.afc.AtomicLong;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
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
public class XaddScalingBench {

    @Param({"AggregatingXadd", "Xadd", "AggregatingAtomicCounter"})
    private String implementation;

    static final boolean unsigned = true;

    private AtomicLong counter;

    @Setup(Level.Trial)
    public void setup() {
        counter = switch (implementation) {
            case "AggregatingXadd" -> new AggregatingXadd(unsigned);
            case "Xadd" -> new Xadd(); // defualt cpu fetch and add instruction
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

    void doWork(Blackhole bh, AtomicLong counter) {
        bh.consume(increment(counter));
    }

    long increment(AtomicLong counter) {
        return switch (counter) {
            case AggregatingXadd ax -> ax.fetchAndIncrement();
            case AggregatingAtomicCounter aac -> increment(aac);
            case Xadd x -> x.fetchAndIncrement();
            default -> throw new IllegalArgumentException();
        };
    }

    long increment(AggregatingAtomicCounter ax) {
        ax.increment();
        return 1;
    }

    static class BenchRunner {
        static void main() throws RunnerException {
            Options options = new OptionsBuilder()
                    .include(XaddScalingBench.class.getSimpleName())
                    .build();
            new org.openjdk.jmh.runner.Runner(options).run();

        }
    }
}

/* FUNNEL-DEPTH = 1
╭ io.github.kusoroadeolu.afc.jmh.XaddScalingBench.eightThread ─╮
│  Implementation           Score  Error   Unit                │
│  ------------------------ ------ ------- ------              │
│  AggregatingXadd          45.802 ± 2.436 ops/us              │
│  Xadd                     31.733 ± 3.139 ops/us              │
│  AggregatingAtomicCounter 59.749 ± 1.894 ops/us              │
╰──────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.afc.jmh.XaddScalingBench.fourThread ─╮
│  Implementation           Score  Error   Unit               │
│  ------------------------ ------ ------- ------             │
│  AggregatingXadd          36.298 ± 3.291 ops/us             │
│  Xadd                     25.405 ± 0.227 ops/us             │
│  AggregatingAtomicCounter 51.327 ± 1.553 ops/us             │
╰─────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.afc.jmh.XaddScalingBench.twoThread ─╮
│  Implementation           Score  Error   Unit              │
│  ------------------------ ------ ------- ------            │
│  AggregatingXadd          43.757 ± 1.975 ops/us            │
│  Xadd                     27.714 ± 1.931 ops/us            │
│  AggregatingAtomicCounter 51.519 ± 1.226 ops/us            │
╰────────────────────────────────────────────────────────────╯
* */

/* FUNNEL-DEPTH = 2
╭ io.github.kusoroadeolu.afc.jmh.XaddScalingBench.eightThread ─╮
│  Implementation           Score  Error   Unit                │
│  ------------------------ ------ ------- ------              │
│  AggregatingXadd          27.672 ± 1.531 ops/us              │
│  AggregatingAtomicCounter 37.543 ± 1.253 ops/us              │
╰──────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.afc.jmh.XaddScalingBench.fourThread ─╮
│  Implementation           Score  Error   Unit               │
│  ------------------------ ------ ------- ------             │
│  AggregatingXadd          23.134 ± 1.029 ops/us             │
│  AggregatingAtomicCounter 31.206 ± 0.422 ops/us             │
╰─────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.afc.jmh.XaddScalingBench.twoThread ─╮
│  Implementation           Score  Error   Unit              │
│  ------------------------ ------ ------- ------            │
│  AggregatingXadd          24.978 ± 0.873 ops/us            │
│  AggregatingAtomicCounter 37.188 ± 1.489 ops/us            │
╰────────────────────────────────────────────────────────────╯
* */


