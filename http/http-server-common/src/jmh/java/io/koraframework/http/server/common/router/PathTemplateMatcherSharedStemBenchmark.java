package io.koraframework.http.server.common.router;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * Measures a collision-heavy shared stem for the production Radix and Hybrid matchers.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@State(Scope.Thread)
public class PathTemplateMatcherSharedStemBenchmark {

    @Param({"16", "128", "1024"})
    public int routes;

    private RadixPathTemplateMatcher<Integer> radixMatcher;
    private HybridPathTemplateMatcher<Integer> hybridMatcher;
    private String hitPath;
    private String missPath;

    @Setup
    public void setup() {
        var radixBuilder = RadixPathTemplateMatcher.<Integer>builder();
        var hybridBuilder = HybridPathTemplateMatcher.<Integer>builder();

        for (int i = 0; i < this.routes; i++) {
            var template = "/shared/{tenant}/resource-" + "%04d".formatted(i) + "/{id}";
            radixBuilder.add(template, i);
            hybridBuilder.add(template, i);
        }
        this.radixMatcher = radixBuilder.build();
        this.hybridMatcher = hybridBuilder.build();

        this.hitPath = "/shared/tenant/resource-" + "%04d".formatted(this.routes - 1) + "/42";
        this.missPath = "/shared/tenant/not-present/42";
    }

    @Benchmark
    public RadixPathTemplateMatcher.PathTemplateMatch<Integer> radixSharedStemHit() {
        return this.radixMatcher.match(this.hitPath);
    }

    @Benchmark
    public HybridPathTemplateMatcher.PathTemplateMatch<Integer> hybridSharedStemHit() {
        return this.hybridMatcher.match(this.hitPath);
    }

    @Benchmark
    public RadixPathTemplateMatcher.PathTemplateMatch<Integer> radixSharedStemMiss() {
        return this.radixMatcher.match(this.missPath);
    }

    @Benchmark
    public HybridPathTemplateMatcher.PathTemplateMatch<Integer> hybridSharedStemMiss() {
        return this.hybridMatcher.match(this.missPath);
    }
}
