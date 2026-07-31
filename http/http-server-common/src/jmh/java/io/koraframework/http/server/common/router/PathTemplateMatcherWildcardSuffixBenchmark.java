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
 * Compares the existing trailing catch-all with an end-anchored wildcard suffix and measures
 * shared-stem fan-out. Only production Radix and Hybrid matchers participate.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(2)
public class PathTemplateMatcherWildcardSuffixBenchmark {

    @State(Scope.Thread)
    public static class SingleRouteState {
        private RadixPathTemplateMatcher<Integer> radixTrailing;
        private HybridPathTemplateMatcher<Integer> hybridTrailing;
        private RadixPathTemplateMatcher<Integer> radixSuffix;
        private HybridPathTemplateMatcher<Integer> hybridSuffix;
        private String trailingPath;
        private String suffixPath;

        @Setup
        public void setup() {
            var radixTrailingBuilder = RadixPathTemplateMatcher.<Integer>builder();
            radixTrailingBuilder.add("/api/files/*", 1);
            this.radixTrailing = radixTrailingBuilder.build();
            var hybridTrailingBuilder = HybridPathTemplateMatcher.<Integer>builder();
            hybridTrailingBuilder.add("/api/files/*", 1);
            this.hybridTrailing = hybridTrailingBuilder.build();
            var radixSuffixBuilder = RadixPathTemplateMatcher.<Integer>builder();
            radixSuffixBuilder.add("/api/files/*.js", 1);
            this.radixSuffix = radixSuffixBuilder.build();
            var hybridSuffixBuilder = HybridPathTemplateMatcher.<Integer>builder();
            hybridSuffixBuilder.add("/api/files/*.js", 1);
            this.hybridSuffix = hybridSuffixBuilder.build();
            this.trailingPath = "/api/files/assets/scripts/application.js";
            this.suffixPath = "/api/files/assets/scripts/application.js";
        }
    }

    @State(Scope.Thread)
    public static class SharedStemState {
        @Param({"1", "4", "16", "64"})
        public int suffixRoutes;

        private RadixPathTemplateMatcher<Integer> radix;
        private HybridPathTemplateMatcher<Integer> hybrid;
        private String firstHitPath;
        private String lastHitPath;
        private String missPath;

        @Setup
        public void setup() {
            var radixBuilder = RadixPathTemplateMatcher.<Integer>builder();
            var hybridBuilder = HybridPathTemplateMatcher.<Integer>builder();
            for (int i = 0; i < this.suffixRoutes; i++) {
                var template = "/api/files/*" + suffix(i);
                radixBuilder.add(template, i);
                hybridBuilder.add(template, i);
            }
            this.radix = radixBuilder.build();
            this.hybrid = hybridBuilder.build();
            this.firstHitPath = "/api/files/application" + suffix(0);
            this.lastHitPath = "/api/files/application" + suffix(this.suffixRoutes - 1);
            this.missPath = "/api/files/application.missing";
        }

        private static String suffix(int route) {
            return ".ext%04d".formatted(route);
        }
    }

    @Benchmark
    public RadixPathTemplateMatcher.PathTemplateMatch<Integer> radixTrailingHit(SingleRouteState state) {
        return state.radixTrailing.match(state.trailingPath);
    }

    @Benchmark
    public HybridPathTemplateMatcher.PathTemplateMatch<Integer> hybridTrailingHit(SingleRouteState state) {
        return state.hybridTrailing.match(state.trailingPath);
    }

    @Benchmark
    public RadixPathTemplateMatcher.PathTemplateMatch<Integer> radixSingleSuffixHit(SingleRouteState state) {
        return state.radixSuffix.match(state.suffixPath);
    }

    @Benchmark
    public HybridPathTemplateMatcher.PathTemplateMatch<Integer> hybridSingleSuffixHit(SingleRouteState state) {
        return state.hybridSuffix.match(state.suffixPath);
    }

    @Benchmark
    public RadixPathTemplateMatcher.PathTemplateMatch<Integer> radixSuffixFirstHit(SharedStemState state) {
        return state.radix.match(state.firstHitPath);
    }

    @Benchmark
    public HybridPathTemplateMatcher.PathTemplateMatch<Integer> hybridSuffixFirstHit(SharedStemState state) {
        return state.hybrid.match(state.firstHitPath);
    }

    @Benchmark
    public RadixPathTemplateMatcher.PathTemplateMatch<Integer> radixSuffixLastHit(SharedStemState state) {
        return state.radix.match(state.lastHitPath);
    }

    @Benchmark
    public HybridPathTemplateMatcher.PathTemplateMatch<Integer> hybridSuffixLastHit(SharedStemState state) {
        return state.hybrid.match(state.lastHitPath);
    }

    @Benchmark
    public RadixPathTemplateMatcher.PathTemplateMatch<Integer> radixSuffixMiss(SharedStemState state) {
        return state.radix.match(state.missPath);
    }

    @Benchmark
    public HybridPathTemplateMatcher.PathTemplateMatch<Integer> hybridSuffixMiss(SharedStemState state) {
        return state.hybrid.match(state.missPath);
    }
}
