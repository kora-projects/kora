package io.koraframework.http.server.common.router;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RadixPathTemplateMatcherTests {

    @Test
    void matchesLikeOldAndNewImplementations() {
        var routes = List.of(
            new Route("/", "root"),
            new Route("/foo", "foo"),
            new Route("/foo/bar", "exact"),
            new Route("/foo/{id}", "single"),
            new Route("/foo/{id}/expected", "single-with-suffix"),
            new Route("/foo/{id}/items/{itemId}", "two"),
            new Route("/foo/{id}/items/{itemId}/detail/{detailId}", "three"),
            new Route("/foo/{id}/*", "parameter-wildcard"),
            new Route("/foo/*", "wildcard"),
            new Route("/other/{id}/", "trailing"),
            new Route("/duplicate/{id}/{id}", "duplicate-name")
        );
        var paths = List.of(
            "", "/", "/foo", "/foo/", "/foo/bar", "/foo/value", "/foo/value/",
            "/foo/value/expected", "/foo/value/other", "/foo/value/items/42",
            "/foo/value/items/42/detail/99", "/foo/value/assets/logo.png",
            "/other/value", "/other/value/", "/duplicate/first/second", "/missing"
        );

        var oldMatcher = new OriginalPathTemplateMatcher<String>();
        var newMatcher = new OptimizedOriginalPathTemplateMatcher<String>();
        var radixBuilder = RadixPathTemplateMatcher.<String>builder();
        for (var route : routes) {
            oldMatcher.add(route.template, route.value);
            newMatcher.add(route.template, route.value);
            radixBuilder.add(route.template, route.value);
        }
        var radixMatcher = radixBuilder.build();

        for (var path : paths) {
            var expected = result(oldMatcher.match(path));
            assertThat(result(newMatcher.match(path))).as("new matcher path %s", path).isEqualTo(expected);
            assertThat(result(radixMatcher.match(path))).as("radix matcher path %s", path).isEqualTo(expected);
        }
    }

    @Test
    void builderSupportsFallbackMutationAndEquivalentTemplates() {
        var builder = RadixPathTemplateMatcher.<String>builder();
        builder.add("/foo/*", "wildcard");
        builder.add("/foo/bar/{id}/expected", "specific");
        var matcher = builder.build();

        assertThat(result(matcher.match("/foo/bar/value/other")))
            .isEqualTo(new Result<>("/foo/*", Map.of("*", "bar/value/other"), "wildcard"));

        var previous = builder.add("/equivalent/{renamed}", "equivalent");
        assertThat(previous).isNull();
        previous = builder.add("/equivalent/{anotherName}", "ignored");
        assertThat(previous).isNotNull();
        assertThat(previous.getValue()).isEqualTo("equivalent");
        assertThat(builder.get("/equivalent/{id}")).isEqualTo("equivalent");

        var withEquivalent = builder.build();
        assertThat(withEquivalent.match("/equivalent/value").value()).isEqualTo("equivalent");

        builder.remove("/equivalent/{renamed}");
        assertThat(builder.get("/equivalent/{id}")).isNull();
        var afterRemoval = builder.build();
        assertThat(afterRemoval.match("/equivalent/value")).isNull();
        assertThat(withEquivalent.match("/equivalent/value").value()).isEqualTo("equivalent");
    }

    @Test
    void compressedTrieUsesFarFewerNodesThanCharacterTrie() {
        var builder = RadixPathTemplateMatcher.<Integer>builder();
        for (int i = 0; i < 1024; i++) {
            builder.add("/api/users/" + i + "/{id}", i);
            builder.add("/api/projects/" + i + "/{projectId}/items/{itemId}", i);
            builder.add("/api/files/" + i + "/*", i);
        }
        var matcher = builder.build();

        assertThat(matcher.radixNodeCount()).isLessThan(4096);
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

    private record Route(String template, String value) {}

    private record Result<T>(String template, Map<String, String> parameters, T value) {}
}
