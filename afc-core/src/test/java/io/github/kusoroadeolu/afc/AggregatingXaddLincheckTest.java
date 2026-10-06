package io.github.kusoroadeolu.afc;

import org.jetbrains.lincheck.Lincheck;
import org.jetbrains.lincheck.datastructures.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AggregatingXaddLincheckTest {

    public AggregatingXadd x = new AggregatingXadd();

    @Operation
    public void fetchAndIncrement() {
        x.fetchAndIncrement();
    }

    @Test
    public void linearizableConcurrentIncrements() {
        new ModelCheckingOptions().check(this.getClass());
    }

    @Test
    public void concurrentIncrementsStress() {
        Lincheck.runConcurrentTest(() -> {
            AggregatingXadd xadd = new AggregatingXadd(1);
            Runnable r = () -> {
                long res = xadd.fetchAndIncrement();
                Assertions.assertTrue(res == 0 || res == 1);
            };

            Thread t = new Thread(r);
            Thread t1 = new Thread(r);
            t.start();
            t1.start();
            try {
                t.join();
                t1.join();
            }catch (InterruptedException _) {}
        });
    }
}