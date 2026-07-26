package io.koraframework.http.server.common.router;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HybridPathTemplateMatcherTests {

    @Test
    void matchesLikeExistingImplementationsWithDecisionBuckets() {
        var routes = List.of(
            new Route("/", "root"),
            new Route("//", "double-root"),
            new Route("/foo", "foo"),
            new Route("/foo/bar", "exact"),
            new Route("/foo/{id}", "single"),
            new Route("/foo/{id}/expected", "single-with-suffix"),
            new Route("/foo/{id}/items/{itemId}", "two"),
            new Route("/foo/{id}/items/{itemId}/detail/{detailId}", "three"),
            new Route("/foo/{id}/*", "parameter-wildcard"),
            new Route("/foo/*", "wildcard"),
            new Route("/other/{id}/", "trailing"),
            new Route("/duplicate/{id}/{id}", "duplicate-name"),
            new Route("/double//{id}", "empty-segment"),
            new Route("/backtrack/static/fixed", "static-branch"),
            new Route("/backtrack/{id}/tail", "parameter-fallback")
        );
        var paths = List.of(
            "", "/", "//", "/foo", "/foo/", "/foo/bar", "/foo/value", "/foo/value/",
            "/foo/value/expected", "/foo/value/other", "/foo/value/items/42",
            "/foo/value/items/42/detail/99", "/foo/value/assets/logo.png",
            "/other/value", "/other/value/", "/duplicate/first/second", "/double//value",
            "/backtrack/static/fixed", "/backtrack/static/tail",
            "/backtrack/static/missing", "/missing"
        );

        var oldMatcher = new OriginalPathTemplateMatcher<String>();
        var hybridBuilder = HybridPathTemplateMatcher.<String>builder(2);
        for (var route : routes) {
            oldMatcher.add(route.template, route.value);
            hybridBuilder.add(route.template, route.value);
        }
        var hybridMatcher = hybridBuilder.build();

        for (var path : paths) {
            assertThat(result(hybridMatcher.match(path)))
                .as("hybrid matcher path %s", path)
                .isEqualTo(result(oldMatcher.match(path)));
        }
    }

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
        assertThat(matcher.match("/decision/acme/resource-7").value()).isEqualTo(7);
        assertThat(matcher.match("/decision/acme/missing")).isNull();
    }

    @Test
    void builderSupportsMutationAndEquivalentTemplates() {
        var builder = HybridPathTemplateMatcher.<String>builder(2);
        builder.add("/foo/*", "wildcard");
        builder.add("/foo/bar/{id}", "specific");
        var matcher = builder.build();

        assertThat(matcher.match("/foo/bar/value").value()).isEqualTo("specific");
        assertThat(matcher.match("/foo/other").value()).isEqualTo("wildcard");

        assertThat(builder.add("/duplicate/{id}", "first")).isNull();
        var previous = builder.add("/duplicate/{renamed}", "second");
        assertThat(previous).isNotNull();
        assertThat(previous.getValue()).isEqualTo("first");
        assertThat(builder.get("/duplicate/{anything}")).isEqualTo("first");

        var withDuplicate = builder.build();
        assertThat(withDuplicate.match("/duplicate/value").value()).isEqualTo("first");

        builder.remove("/duplicate/{id}");
        assertThat(builder.get("/duplicate/{anything}")).isNull();
        assertThat(builder.build().match("/duplicate/value")).isNull();
        assertThat(withDuplicate.match("/duplicate/value").value()).isEqualTo("first");
    }

    private static <T> Result<T> result(OriginalPathTemplateMatcher.PathTemplateMatch<T> match) {
        return match == null ? null : new Result<>(match.matchedTemplate(), match.parameters(), match.value());
    }

    private static <T> Result<T> result(HybridPathTemplateMatcher.PathTemplateMatch<T> match) {
        return match == null ? null : new Result<>(match.matchedTemplate(), match.parameters(), match.value());
    }

    private record Route(String template, String value) {}

    private record Result<T>(String template, Map<String, String> parameters, T value) {}
}
