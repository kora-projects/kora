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

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@State(Scope.Thread)
public class PathTemplateMatcherBenchmark {

    @Param({"16", "128", "1024"})
    public int routes;

    private OriginalPathTemplateMatcher<Integer> originalMatcher;
    private OptimizedOriginalPathTemplateMatcher<Integer> optimizedOriginalMatcher;
    private RadixPathTemplateMatcher<Integer> radixMatcher;
    private DecisionPathTemplateMatcher<Integer> decisionMatcher;
    private HybridPathTemplateMatcher<Integer> hybridMatcher;
    private String exactPath;
    private String singleParameterPath;
    private String multiParameterPath;
    private String wildcardPath;
    private String missPath;

    @Setup
    public void setup() {
        this.originalMatcher = new OriginalPathTemplateMatcher<>();
        this.optimizedOriginalMatcher = new OptimizedOriginalPathTemplateMatcher<>();
        this.decisionMatcher = new DecisionPathTemplateMatcher<>();
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
                this.originalMatcher.add(template, i);
                this.optimizedOriginalMatcher.add(template, i);
                radixBuilder.add(template, i);
                this.decisionMatcher.add(template, i);
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
    public OriginalPathTemplateMatcher.PathTemplateMatch<Integer> originalExact() {
        return this.originalMatcher.match(this.exactPath);
    }

    @Benchmark
    public OptimizedOriginalPathTemplateMatcher.PathTemplateMatch<Integer> optimizedOriginalExact() {
        return this.optimizedOriginalMatcher.match(this.exactPath);
    }

    @Benchmark
    public RadixPathTemplateMatcher.PathTemplateMatch<Integer> radixExact() {
        return this.radixMatcher.match(this.exactPath);
    }

    @Benchmark
    public DecisionPathTemplateMatcher.PathTemplateMatch<Integer> decisionExact() {
        return this.decisionMatcher.match(this.exactPath);
    }

    @Benchmark
    public HybridPathTemplateMatcher.PathTemplateMatch<Integer> hybridExact() {
        return this.hybridMatcher.match(this.exactPath);
    }

    @Benchmark
    public OriginalPathTemplateMatcher.PathTemplateMatch<Integer> originalSingleParameter() {
        return this.originalMatcher.match(this.singleParameterPath);
    }

    @Benchmark
    public OptimizedOriginalPathTemplateMatcher.PathTemplateMatch<Integer> optimizedOriginalSingleParameter() {
        return this.optimizedOriginalMatcher.match(this.singleParameterPath);
    }

    @Benchmark
    public RadixPathTemplateMatcher.PathTemplateMatch<Integer> radixSingleParameter() {
        return this.radixMatcher.match(this.singleParameterPath);
    }

    @Benchmark
    public DecisionPathTemplateMatcher.PathTemplateMatch<Integer> decisionSingleParameter() {
        return this.decisionMatcher.match(this.singleParameterPath);
    }

    @Benchmark
    public HybridPathTemplateMatcher.PathTemplateMatch<Integer> hybridSingleParameter() {
        return this.hybridMatcher.match(this.singleParameterPath);
    }

    @Benchmark
    public OriginalPathTemplateMatcher.PathTemplateMatch<Integer> originalMultiParameter() {
        return this.originalMatcher.match(this.multiParameterPath);
    }

    @Benchmark
    public OptimizedOriginalPathTemplateMatcher.PathTemplateMatch<Integer> optimizedOriginalMultiParameter() {
        return this.optimizedOriginalMatcher.match(this.multiParameterPath);
    }

    @Benchmark
    public RadixPathTemplateMatcher.PathTemplateMatch<Integer> radixMultiParameter() {
        return this.radixMatcher.match(this.multiParameterPath);
    }

    @Benchmark
    public DecisionPathTemplateMatcher.PathTemplateMatch<Integer> decisionMultiParameter() {
        return this.decisionMatcher.match(this.multiParameterPath);
    }

    @Benchmark
    public HybridPathTemplateMatcher.PathTemplateMatch<Integer> hybridMultiParameter() {
        return this.hybridMatcher.match(this.multiParameterPath);
    }

    @Benchmark
    public OriginalPathTemplateMatcher.PathTemplateMatch<Integer> originalWildcard() {
        return this.originalMatcher.match(this.wildcardPath);
    }

    @Benchmark
    public OptimizedOriginalPathTemplateMatcher.PathTemplateMatch<Integer> optimizedOriginalWildcard() {
        return this.optimizedOriginalMatcher.match(this.wildcardPath);
    }

    @Benchmark
    public RadixPathTemplateMatcher.PathTemplateMatch<Integer> radixWildcard() {
        return this.radixMatcher.match(this.wildcardPath);
    }

    @Benchmark
    public DecisionPathTemplateMatcher.PathTemplateMatch<Integer> decisionWildcard() {
        return this.decisionMatcher.match(this.wildcardPath);
    }

    @Benchmark
    public HybridPathTemplateMatcher.PathTemplateMatch<Integer> hybridWildcard() {
        return this.hybridMatcher.match(this.wildcardPath);
    }

    @Benchmark
    public OriginalPathTemplateMatcher.PathTemplateMatch<Integer> originalMiss() {
        return this.originalMatcher.match(this.missPath);
    }

    @Benchmark
    public OptimizedOriginalPathTemplateMatcher.PathTemplateMatch<Integer> optimizedOriginalMiss() {
        return this.optimizedOriginalMatcher.match(this.missPath);
    }

    @Benchmark
    public RadixPathTemplateMatcher.PathTemplateMatch<Integer> radixMiss() {
        return this.radixMatcher.match(this.missPath);
    }

    @Benchmark
    public DecisionPathTemplateMatcher.PathTemplateMatch<Integer> decisionMiss() {
        return this.decisionMatcher.match(this.missPath);
    }

    @Benchmark
    public HybridPathTemplateMatcher.PathTemplateMatch<Integer> hybridMiss() {
        return this.hybridMatcher.match(this.missPath);
    }
}
