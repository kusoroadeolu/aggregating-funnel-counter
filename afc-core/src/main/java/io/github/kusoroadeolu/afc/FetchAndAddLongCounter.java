package io.github.kusoroadeolu.afc;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class FetchAndAddLongCounter implements AtomicLongCounter {
    private volatile long value;

    private static final VarHandle VALUE;


    @Override
    public void increment() {
        VALUE.getAndAdd(this, 1);
    }

    @Override
    public void decrement() {
        VALUE.getAndAdd(this, -1);
    }

    @Override
    public long sum() {
        return value;
    }

    static {
        var l = MethodHandles.lookup();
        try {
            VALUE = l.findVarHandle(FetchAndAddLongCounter.class, "value", long.class);
        }catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
