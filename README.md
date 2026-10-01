# Fetch And Add Aggregating Funnel
This project contains a faithfully unfaithful implementation of the paper [Aggregating Funnels for Faster Fetch&Add](https://arxiv.org/pdf/2411.14420)

It includes two custom implementations 
1. `AggregatingAtomicCounter` - is not linearizable as callers do not return a value. This class is expected to provide higher throughput and lower latency than its counterpart as it skips a lot of machinery
needed to provide a return value. It can be used for implementing striped counters, similar to `LongAdder`
2. `AggregatingXadd` - is linearizable and pretty similar to how the hardware instruction operates.

**Note** Both implementations are `blocking` and not `wait-free`, though I believe its throughput scaling compensates for it

## Benchmarks
This project also includes a benchmark in the `afc-jmh` module. This benchmark tests thread scaling under high write contention

### Benchmark setup
- **Threads:** 2, 4, 8
- **JMH:** 5 warmup iterations, 10 measurement iterations, 2 forks
- **Mode:** Throughput in ops/µs (higher is better)

### Environment
There are some benchmark numbers in the `XaddScalingBench` class. These numbers were gotten from this environment. 

**Note** These numbers can differ across different machines due to multiple factors.

| |                        |
|---|------------------------|
| CPU | Intel i5               |
| Cores / threads | 4 cores - 8 processors |
| RAM | 16GB                   |
| OS | Windows 11             |
| JDK | 25 (Open JDK)          |
| GC / heap flags | -Xms8g, -Xmx8g, -XX:+UseG1GC       |

## Running the benchmarks
To run the benchmarks for yourself you need to have JDK 25 and Maven installed.

```bash
mvn clean package
cd afc-jmh
java -jar target/benchmark.jar
```