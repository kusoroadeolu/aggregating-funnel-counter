package io.github.kusoroadeolu.afc.jmh;

import io.github.kusoroadeolu.afc.AtomicLongCounter;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class Xadd implements AtomicLongCounter {
    private volatile long value;
    private static final VarHandle VALUE;

    public long fetchAndIncrement() {
       return (long) VALUE.getAndAdd(this, 1);
    }

    public long fetchAndDecrement() {
       return (long) VALUE.getAndAdd(this, -1);
    }

    public boolean compareAndSet(long from, long to) {
        return VALUE.compareAndSet(this, from, to);
    }

    public long get() {
        return value;
    }

    static {
        var l = MethodHandles.lookup();
        try {
            VALUE = l.findVarHandle(Xadd.class, "value", long.class);
        }catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
