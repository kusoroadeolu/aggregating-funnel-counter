package io.github.kusoroadeolu.afc;

import org.jctools.counters.Counter;
import org.jctools.counters.CountersFactory;

public class JCToolsLongCounter implements LongCounter {

    final Counter counter;

    public JCToolsLongCounter() {
        this.counter = CountersFactory.createFixedSizeStripedCounterV8(Runtime.getRuntime().availableProcessors());
    }

    @Override
    public void increment() {
        counter.inc();
    }

    @Override
    public void decrement() {
        counter.inc(-1);
    }

    @Override
    public long sum() {
        return counter.get();
    }
}
