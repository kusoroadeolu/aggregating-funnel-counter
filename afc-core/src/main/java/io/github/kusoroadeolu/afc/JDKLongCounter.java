package io.github.kusoroadeolu.afc;

import java.util.concurrent.atomic.LongAdder;

public class JDKLongCounter implements LongCounter {
    public final LongAdder adder;

    public JDKLongCounter() {
        this.adder = new LongAdder();
    }

    @Override
    public void increment() {
        adder.increment();
    }

    @Override
    public void decrement() {
        adder.decrement();
    }

    @Override
    public long sum() {
        return adder.sum();
    }
}
