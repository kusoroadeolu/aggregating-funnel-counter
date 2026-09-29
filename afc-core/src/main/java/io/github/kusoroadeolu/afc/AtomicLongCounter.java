package io.github.kusoroadeolu.afc;

public interface AtomicLongCounter {
    long fetchAndIncrement();
    long fetchAndDecrement();
    long value();
}
