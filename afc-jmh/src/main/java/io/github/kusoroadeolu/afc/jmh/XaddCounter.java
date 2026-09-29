package io.github.kusoroadeolu.afc.jmh;

import io.github.kusoroadeolu.afc.AtomicLongCounter;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class XaddCounter implements AtomicLongCounter {
    private volatile long value;

    private static final VarHandle VALUE;


    @Override
    public long fetchAndIncrement() {
       return (long) VALUE.getAndAdd(this, 1);
    }

    @Override
    public long fetchAndDecrement() {
       return (long) VALUE.getAndAdd(this, -1);
    }

    @Override
    public boolean compareAndSet(long from, long to) {
        return VALUE.compareAndSet(this, from, to);
    }

    @Override
    public long value() {
        return value;
    }

    static {
        var l = MethodHandles.lookup();
        try {
            VALUE = l.findVarHandle(XaddCounter.class, "value", long.class);
        }catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
