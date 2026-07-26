package io.koraframework.http.server.common.router;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionPathTemplateMatcherTests {

    @Test
    void matchesLikeAllExistingImplementations() {
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
        var newMatcher = new OptimizedOriginalPathTemplateMatcher<String>();
        var radixBuilder = RadixPathTemplateMatcher.<String>builder();
        var decisionMatcher = new DecisionPathTemplateMatcher<String>();
        for (var route : routes) {
            oldMatcher.add(route.template, route.value);
            newMatcher.add(route.template, route.value);
            radixBuilder.add(route.template, route.value);
            decisionMatcher.add(route.template, route.value);
        }
        var radixMatcher = radixBuilder.build();

        for (var path : paths) {
            var expected = result(oldMatcher.match(path));
            assertThat(result(newMatcher.match(path))).as("new matcher path %s", path).isEqualTo(expected);
            assertThat(result(radixMatcher.match(path))).as("radix matcher path %s", path).isEqualTo(expected);
            assertThat(result(decisionMatcher.match(path))).as("decision matcher path %s", path).isEqualTo(expected);
        }
    }

    @Test
    void supportsMutationAndEquivalentTemplates() {
        var matcher = new DecisionPathTemplateMatcher<String>();
        matcher.add("/foo/*", "wildcard");
        matcher.add("/foo/bar/{id}", "specific");

        assertThat(matcher.match("/foo/bar/value").value()).isEqualTo("specific");
        assertThat(matcher.match("/foo/other").value()).isEqualTo("wildcard");

        var previous = matcher.add("/duplicate/{id}", "first");
        assertThat(previous).isNull();
        previous = matcher.add("/duplicate/{renamed}", "second");
        assertThat(previous).isNotNull();
        assertThat(previous.getValue()).isEqualTo("first");
        assertThat(matcher.get("/duplicate/{anything}")).isEqualTo("first");

        matcher.remove("/duplicate/{id}");
        assertThat(matcher.get("/duplicate/{anything}")).isNull();
    }

    @Test
    void storesRoutesBySegmentsRatherThanCharacters() {
        var matcher = new DecisionPathTemplateMatcher<Integer>();
        for (int i = 0; i < 128; i++) {
            matcher.add("/api/users/" + i + "/{id}", i);
            matcher.add("/api/projects/" + i + "/{projectId}/items/{itemId}", i);
            matcher.add("/api/files/" + i + "/*", i);
        }

        assertThat(matcher.decisionNodeCount()).isLessThan(1024);
    }

    private static <T> Result<T> result(OriginalPathTemplateMatcher.PathTemplateMatch<T> match) {
        return match == null ? null : new Result<>(match.matchedTemplate(), match.parameters(), match.value());
    }

    private static <T> Result<T> result(OptimizedOriginalPathTemplateMatcher.PathTemplateMatch<T> match) {
        return match == null ? null : new Result<>(match.matchedTemplate(), match.parameters(), match.value());
    }

    private static <T> Result<T> result(RadixPathTemplateMatcher.PathTemplateMatch<T> match) {
        return match == null ? null : new Result<>(match.matchedTemplate(), match.parameters(), match.value());
    }

    private static <T> Result<T> result(DecisionPathTemplateMatcher.PathTemplateMatch<T> match) {
        return match == null ? null : new Result<>(match.matchedTemplate(), match.parameters(), match.value());
    }

    private record Route(String template, String value) {}

    private record Result<T>(String template, Map<String, String> parameters, T value) {}
}
