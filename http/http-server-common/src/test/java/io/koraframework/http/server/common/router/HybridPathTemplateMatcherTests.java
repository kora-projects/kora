package io.koraframework.http.server.common.router;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HybridPathTemplateMatcherTests {

    @Test
    void compilesStrategyFromDynamicStemGroupSize() {
        var builder = HybridPathTemplateMatcher.<Integer>builder(8);
        builder.add("/single/{id}", 0);
        for (int i = 0; i < 7; i++) {
            builder.add("/linear/{tenant}/resource-" + i, i);
        }
        for (int i = 0; i < 8; i++) {
            builder.add("/decision/{tenant}/resource-" + i, i);
        }
        var matcher = builder.build();

        assertThat(matcher.singleStemCount()).isEqualTo(1);
        assertThat(matcher.linearStemCount()).isEqualTo(1);
        assertThat(matcher.decisionStemCount()).isEqualTo(1);
        assertThat(matcher.match("/single/value").value()).isZero();
        assertThat(matcher.match("/linear/acme/resource-6").value()).isEqualTo(6);
        assertThat(matcher.match("/decision/acme/resource-7").value()).isEqualTo(7);
        assertThat(matcher.match("/decision/acme/missing")).isNull();
    }

    @Test
    void defaultThresholdCompilesEverySharedStemIntoDecisionTrie() {
        var builder = HybridPathTemplateMatcher.<Integer>builder();
        builder.add("/shared/{tenant}/resource-a/{id}", 1);
        builder.add("/shared/{tenant}/resource-b/{id}", 2);
        var matcher = builder.build();

        assertThat(matcher.singleStemCount()).isZero();
        assertThat(matcher.linearStemCount()).isZero();
        assertThat(matcher.decisionStemCount()).isEqualTo(1);
        assertThat(matcher.match("/shared/acme/resource-a/42").value()).isEqualTo(1);
        assertThat(matcher.match("/shared/acme/resource-b/42").value()).isEqualTo(2);
        assertThat(matcher.match("/shared/acme/resource-c/42")).isNull();
    }

    @Test
    void decisionTrieHandlesLargeSharedStemWithoutLinearFallback() {
        var builder = HybridPathTemplateMatcher.<Integer>builder();
        for (int i = 0; i < 1024; i++) {
            builder.add("/shared/{tenant}/resource-" + "%04d".formatted(i) + "/{id}", i);
        }
        var matcher = builder.build();

        assertThat(matcher.decisionStemCount()).isEqualTo(1);
        assertThat(matcher.match("/shared/acme/resource-0000/42").value()).isZero();
        assertThat(matcher.match("/shared/acme/resource-0512/42").value()).isEqualTo(512);
        assertThat(matcher.match("/shared/acme/resource-1023/42").value()).isEqualTo(1023);
        assertThat(matcher.match("/shared/acme/not-present/42")).isNull();
    }
}
