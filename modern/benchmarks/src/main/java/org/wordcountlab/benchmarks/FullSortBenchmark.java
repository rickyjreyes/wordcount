package org.wordcountlab.benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.wordcountlab.Frequency;
import org.wordcountlab.Sorts;
import org.wordcountlab.experimental.ExperimentLab;
import org.wordcountlab.experimental.ResearchSorts;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/** End-to-end in-memory sort benchmark: includes input clone and all method setup,
 * excludes dataset construction and reference verification (both in trial setup).
 *
 * This measures the actual public ResearchSorts API, including its counters and
 * Result construction. It does NOT isolate algorithm-only performance, and the
 * different counter instrumentation per method is explicitly a limitation.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations=5,time=1,timeUnit=TimeUnit.SECONDS)
@Measurement(iterations=7,time=1,timeUnit=TimeUnit.SECONDS)
@Fork(value=2)
public class FullSortBenchmark {
    @Param({"random","zipf","sparse","equal","sorted","reverse","unicode","prefix"})
    public String dataset;

    @Param({"2048","8192","32768"})
    public int n;

    @Param({"2017","42","73","101","4099"})
    public int seed;

    @Param({"freqbucket","merge","jdk-native","radix","sparsebucket","pdq","parallel","hybrid"})
    public String algorithm;

    private Frequency[] input;

    @Setup(Level.Trial)
    public void prepare() {
        input=ExperimentLab.generated(dataset,n,seed);
        Frequency[] oracle=input.clone();
        Arrays.sort(oracle,Sorts.ORDER);
        var preflight=ResearchSorts.sort(input,algorithm);
        if(!Arrays.equals(oracle,preflight.values()))
            throw new IllegalStateException("Reference mismatch: "+dataset+"/"+n+"/"+seed+"/"+algorithm);
        if(n>1 && algorithm.equals("freqbucket") && dataset.equals("sparse")
           && !preflight.implementation().contains("merge(sparse-frequency-range)"))
            throw new IllegalStateException("Expected labeled sparse fallback");
    }

    @Benchmark
    public void fullSort(Blackhole bh) {
        bh.consume(ResearchSorts.sort(input,algorithm).values());
    }
}
