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
 * General route-shape comparison for the production Radix and Hybrid matchers.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@State(Scope.Thread)
public class PathTemplateMatcherBenchmark {

    @Param({"16", "128", "1024"})
    public int routes;

    private RadixPathTemplateMatcher<Integer> radixMatcher;
    private HybridPathTemplateMatcher<Integer> hybridMatcher;
    private String exactPath;
    private String singleParameterPath;
    private String multiParameterPath;
    private String wildcardPath;
    private String missPath;

    @Setup
    public void setup() {
        var radixBuilder = RadixPathTemplateMatcher.<Integer>builder();
        var hybridBuilder = HybridPathTemplateMatcher.<Integer>builder();

        for (int i = 0; i < this.routes; i++) {
            var templates = new String[]{
                "/api/static/" + i,
                "/api/users/" + i + "/{id}",
                "/api/projects/" + i + "/{projectId}/items/{itemId}",
                "/api/files/" + i + "/*"
            };
            for (var template : templates) {
                radixBuilder.add(template, i);
                hybridBuilder.add(template, i);
            }
        }
        this.radixMatcher = radixBuilder.build();
        this.hybridMatcher = hybridBuilder.build();

        int last = this.routes - 1;
        this.exactPath = "/api/static/" + last;
        this.singleParameterPath = "/api/users/" + last + "/42";
        this.multiParameterPath = "/api/projects/" + last + "/project-42/items/item-42";
        this.wildcardPath = "/api/files/" + last + "/assets/images/logo.png";
        this.missPath = "/api/missing/" + last + "/42";
    }

    @Benchmark
    public RadixPathTemplateMatcher.PathTemplateMatch<Integer> radixExact() {
        return this.radixMatcher.match(this.exactPath);
    }

    @Benchmark
    public HybridPathTemplateMatcher.PathTemplateMatch<Integer> hybridExact() {
        return this.hybridMatcher.match(this.exactPath);
    }

    @Benchmark
    public RadixPathTemplateMatcher.PathTemplateMatch<Integer> radixSingleParameter() {
        return this.radixMatcher.match(this.singleParameterPath);
    }

    @Benchmark
    public HybridPathTemplateMatcher.PathTemplateMatch<Integer> hybridSingleParameter() {
        return this.hybridMatcher.match(this.singleParameterPath);
    }

    @Benchmark
    public RadixPathTemplateMatcher.PathTemplateMatch<Integer> radixMultiParameter() {
        return this.radixMatcher.match(this.multiParameterPath);
    }

    @Benchmark
    public HybridPathTemplateMatcher.PathTemplateMatch<Integer> hybridMultiParameter() {
        return this.hybridMatcher.match(this.multiParameterPath);
    }

    @Benchmark
    public RadixPathTemplateMatcher.PathTemplateMatch<Integer> radixWildcard() {
        return this.radixMatcher.match(this.wildcardPath);
    }

    @Benchmark
    public HybridPathTemplateMatcher.PathTemplateMatch<Integer> hybridWildcard() {
        return this.hybridMatcher.match(this.wildcardPath);
    }

    @Benchmark
    public RadixPathTemplateMatcher.PathTemplateMatch<Integer> radixMiss() {
        return this.radixMatcher.match(this.missPath);
    }

    @Benchmark
    public HybridPathTemplateMatcher.PathTemplateMatch<Integer> hybridMiss() {
        return this.hybridMatcher.match(this.missPath);
    }
}
