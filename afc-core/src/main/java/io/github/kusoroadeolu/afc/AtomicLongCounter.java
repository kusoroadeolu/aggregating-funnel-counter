package io.github.kusoroadeolu.afc;

public interface AtomicLongCounter {
    long fetchAndIncrement();
    long fetchAndDecrement();
    long value();
    boolean compareAndSet(long from, long to);
}
