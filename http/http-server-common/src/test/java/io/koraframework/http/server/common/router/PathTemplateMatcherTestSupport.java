package io.koraframework.http.server.common.router;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class PathTemplateMatcherTestSupport {

    private PathTemplateMatcherTestSupport() {}

    enum Implementation {
        RADIX {
            @Override
            TestBuilder builder() {
                return new RadixBuilder();
            }
        },
        HYBRID {
            @Override
            TestBuilder builder() {
                return new HybridBuilder();
            }
        };

        abstract TestBuilder builder();
    }

    interface TestBuilder {

        @Nullable
        Previous add(String template, String value);

        void addAll(TestBuilder source);

        void remove(String template);

        @Nullable
        String get(String template);

        Set<String> templates();

        TestMatcher build();
    }

    interface TestMatcher {

        @Nullable
        Match match(String path);
    }

    record Previous(String template, String value) {}

    record Match(String template, Map<String, String> parameters, String value) {}

    private static final class RadixBuilder implements TestBuilder {
        private final RadixPathTemplateMatcher.Builder<String> delegate = RadixPathTemplateMatcher.builder();

        @Override
        public @Nullable Previous add(String template, String value) {
            var previous = this.delegate.add(template, value);
            return previous == null
                ? null
                : new Previous(previous.getKey().templateString(), previous.getValue());
        }

        @Override
        public void addAll(TestBuilder source) {
            this.delegate.addAll(((RadixBuilder) source).delegate);
        }

        @Override
        public void remove(String template) {
            this.delegate.remove(template);
        }

        @Override
        public @Nullable String get(String template) {
            return this.delegate.get(template);
        }

        @Override
        public Set<String> templates() {
            return this.delegate.getPathTemplates().stream()
                .map(RadixPathTemplate::templateString)
                .collect(Collectors.toSet());
        }

        @Override
        public TestMatcher build() {
            var matcher = this.delegate.build();
            return path -> {
                var match = matcher.match(path);
                return match == null
                    ? null
                    : new Match(match.matchedTemplate(), match.parameters(), match.value());
            };
        }
    }

    private static final class HybridBuilder implements TestBuilder {
        private final HybridPathTemplateMatcher.Builder<String> delegate = HybridPathTemplateMatcher.builder();

        @Override
        public @Nullable Previous add(String template, String value) {
            var previous = this.delegate.add(template, value);
            return previous == null
                ? null
                : new Previous(previous.getKey().templateString(), previous.getValue());
        }

        @Override
        public void addAll(TestBuilder source) {
            this.delegate.addAll(((HybridBuilder) source).delegate);
        }

        @Override
        public void remove(String template) {
            this.delegate.remove(template);
        }

        @Override
        public @Nullable String get(String template) {
            return this.delegate.get(template);
        }

        @Override
        public Set<String> templates() {
            return this.delegate.getPathTemplates().stream()
                .map(HybridPathTemplate::templateString)
                .collect(Collectors.toSet());
        }

        @Override
        public TestMatcher build() {
            var matcher = this.delegate.build();
            return path -> {
                var match = matcher.match(path);
                return match == null
                    ? null
                    : new Match(match.matchedTemplate(), match.parameters(), match.value());
            };
        }
    }
}
