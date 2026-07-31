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
 * Measures crossover between linear and decision matching inside radix stem buckets.
 * Total route count stays fixed; only candidates per shared stem change.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@State(Scope.Thread)
public class PathTemplateMatcherHybridGroupsBenchmark {

    private static final int ROUTES = 128;

    @Param({"1", "2", "4", "8", "16", "32", "64", "128"})
    public int groupSize;

    @Param({"EARLY_FANOUT", "DEEP_FANOUT", "WILDCARD_FANOUT"})
    public String shape;

    private RadixPathTemplateMatcher<Integer> radixMatcher;
    private HybridPathTemplateMatcher<Integer> hybridDefaultMatcher;
    private HybridPathTemplateMatcher<Integer> hybridAlwaysLinearMatcher;
    private HybridPathTemplateMatcher<Integer> hybridAlwaysDecisionMatcher;
    private String hitPath;
    private String missPath;

    @Setup
    public void setup() {
        var radixBuilder = RadixPathTemplateMatcher.<Integer>builder();
        var hybridDefaultBuilder = HybridPathTemplateMatcher.<Integer>builder();
        var hybridAlwaysLinearBuilder = HybridPathTemplateMatcher.<Integer>builder(Integer.MAX_VALUE);
        var hybridAlwaysDecisionBuilder = HybridPathTemplateMatcher.<Integer>builder(2);

        int groups = ROUTES / this.groupSize;
        for (int group = 0; group < groups; group++) {
            for (int candidate = 0; candidate < this.groupSize; candidate++) {
                var template = this.template(group, candidate);
                int value = group * this.groupSize + candidate;
                radixBuilder.add(template, value);
                hybridDefaultBuilder.add(template, value);
                hybridAlwaysLinearBuilder.add(template, value);
                hybridAlwaysDecisionBuilder.add(template, value);
            }
        }
        this.radixMatcher = radixBuilder.build();
        this.hybridDefaultMatcher = hybridDefaultBuilder.build();
        this.hybridAlwaysLinearMatcher = hybridAlwaysLinearBuilder.build();
        this.hybridAlwaysDecisionMatcher = hybridAlwaysDecisionBuilder.build();

        int targetGroup = groups - 1;
        this.hitPath = this.hitPath(targetGroup);
        this.missPath = this.missPath(targetGroup);
    }

    private String template(int group, int candidate) {
        var prefix = "/group-" + group + "/shared/{tenant}/";
        return switch (this.shape) {
            case "EARLY_FANOUT" -> prefix + "resource-" + routeId(candidate) + "/{id}";
            case "DEEP_FANOUT" -> prefix + "common/{kind}/v1/resource-" + routeId(candidate) + "/{id}";
            case "WILDCARD_FANOUT" -> prefix + "resource-" + routeId(candidate) + "/*";
            default -> throw new IllegalStateException("Unknown shape: " + this.shape);
        };
    }

    private String hitPath(int group) {
        var prefix = "/group-" + group + "/shared/acme/";
        return switch (this.shape) {
            case "EARLY_FANOUT" -> prefix + "resource-" + routeId(this.groupSize - 1) + "/42";
            case "DEEP_FANOUT" -> prefix + "common/type/v1/resource-" + routeId(this.groupSize - 1) + "/42";
            case "WILDCARD_FANOUT" ->
                prefix + "resource-" + routeId(this.groupSize - 1) + "/assets/logo.png";
            default -> throw new IllegalStateException("Unknown shape: " + this.shape);
        };
    }

    private String missPath(int group) {
        var prefix = "/group-" + group + "/shared/acme/";
        return switch (this.shape) {
            case "EARLY_FANOUT", "WILDCARD_FANOUT" -> prefix + "not-present/42";
            case "DEEP_FANOUT" -> prefix + "common/type/v1/not-present/42";
            default -> throw new IllegalStateException("Unknown shape: " + this.shape);
        };
    }

    private static String routeId(int candidate) {
        return "%04d".formatted(candidate);
    }

    @Benchmark
    public RadixPathTemplateMatcher.PathTemplateMatch<Integer> radixHit() {
        return this.radixMatcher.match(this.hitPath);
    }

    @Benchmark
    public HybridPathTemplateMatcher.PathTemplateMatch<Integer> hybridDefaultHit() {
        return this.hybridDefaultMatcher.match(this.hitPath);
    }

    @Benchmark
    public HybridPathTemplateMatcher.PathTemplateMatch<Integer> hybridAlwaysLinearHit() {
        return this.hybridAlwaysLinearMatcher.match(this.hitPath);
    }

    @Benchmark
    public HybridPathTemplateMatcher.PathTemplateMatch<Integer> hybridAlwaysDecisionHit() {
        return this.hybridAlwaysDecisionMatcher.match(this.hitPath);
    }

    @Benchmark
    public RadixPathTemplateMatcher.PathTemplateMatch<Integer> radixMiss() {
        return this.radixMatcher.match(this.missPath);
    }

    @Benchmark
    public HybridPathTemplateMatcher.PathTemplateMatch<Integer> hybridDefaultMiss() {
        return this.hybridDefaultMatcher.match(this.missPath);
    }

    @Benchmark
    public HybridPathTemplateMatcher.PathTemplateMatch<Integer> hybridAlwaysLinearMiss() {
        return this.hybridAlwaysLinearMatcher.match(this.missPath);
    }

    @Benchmark
    public HybridPathTemplateMatcher.PathTemplateMatch<Integer> hybridAlwaysDecisionMiss() {
        return this.hybridAlwaysDecisionMatcher.match(this.missPath);
    }
}
