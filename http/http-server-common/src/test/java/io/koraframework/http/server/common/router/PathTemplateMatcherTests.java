package io.koraframework.http.server.common.router;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Map;

import static io.koraframework.http.server.common.router.PathTemplateMatcherTestSupport.Implementation;
import static io.koraframework.http.server.common.router.PathTemplateMatcherTestSupport.Match;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PathTemplateMatcherTests {

    @ParameterizedTest
    @EnumSource(Implementation.class)
    void matchesExactParameterAndWildcardRoutes(Implementation implementation) {
        var builder = implementation.builder();
        var routes = List.of(
            new Route("/", "root"),
            new Route("//", "double-root"),
            new Route("/foo", "foo"),
            new Route("/foo/bar", "exact"),
            new Route("/single/{id}", "single"),
            new Route("/suffix/{id}/expected", "single-suffix"),
            new Route("/items/{id}/items/{itemId}", "two"),
            new Route("/detail/{id}/items/{itemId}/detail/{detailId}", "three"),
            new Route("/wild/{id}/*", "parameter-wildcard"),
            new Route("/catch/*", "wildcard"),
            new Route("/other/{id}/", "trailing"),
            new Route("/duplicate/{id}/{id}", "duplicate-name"),
            new Route("/double//{id}", "empty-segment")
        );
        routes.forEach(route -> builder.add(route.template(), route.value()));
        var matcher = builder.build();

        var cases = List.of(
            new Case("", match("/", "root")),
            new Case("/", match("/", "root")),
            new Case("//", match("//", "double-root")),
            new Case("/foo", match("/foo", "foo")),
            new Case("/foo/", null),
            new Case("/foo/bar", match("/foo/bar", "exact")),
            new Case("/single/value", match("/single/{id}", Map.of("id", "value"), "single")),
            new Case("/suffix/value/expected", match(
                "/suffix/{id}/expected", Map.of("id", "value"), "single-suffix"
            )),
            new Case("/items/value/items/42", match(
                "/items/{id}/items/{itemId}", Map.of("id", "value", "itemId", "42"), "two"
            )),
            new Case("/detail/value/items/42/detail/99", match(
                "/detail/{id}/items/{itemId}/detail/{detailId}",
                Map.of("id", "value", "itemId", "42", "detailId", "99"),
                "three"
            )),
            new Case("/wild/value/", match(
                "/wild/{id}/*", Map.of("id", "value", "*", ""), "parameter-wildcard"
            )),
            new Case("/wild/value/assets/logo.png", match(
                "/wild/{id}/*", Map.of("id", "value", "*", "assets/logo.png"), "parameter-wildcard"
            )),
            new Case("/catch/", match("/catch/*", Map.of("*", ""), "wildcard")),
            new Case("/catch/value/other", match(
                "/catch/*", Map.of("*", "value/other"), "wildcard"
            )),
            new Case("/other/value", null),
            new Case("/other/value/", match("/other/{id}/", Map.of("id", "value"), "trailing")),
            new Case("/duplicate/first/second", match(
                "/duplicate/{id}/{id}", Map.of("id", "second"), "duplicate-name"
            )),
            new Case("/double//value", match("/double//{id}", Map.of("id", "value"), "empty-segment")),
            new Case("/missing", null)
        );

        for (var testCase : cases) {
            assertThat(matcher.match(testCase.path()))
                .as("%s path %s", implementation, testCase.path())
                .isEqualTo(testCase.expected());
        }
    }

    @ParameterizedTest
    @EnumSource(Implementation.class)
    void preservesRoutePriorityAndBacktracks(Implementation implementation) {
        var builder = implementation.builder();
        builder.add("/priority/*", "wildcard");
        builder.add("/priority/{id}", "parameter");
        builder.add("/priority/static", "static");
        builder.add("/priority/{id}/tail", "longer");
        builder.add("/specific/*", "shorter-wildcard");
        builder.add("/specific/static/{id}/tail", "longer-stem");
        builder.add("/fallback/*", "fallback");
        builder.add("/fallback/static/{id}/expected", "specific");
        builder.add("/branch/static/fixed", "static-branch");
        builder.add("/branch/{id}/tail", "parameter-branch");
        var matcher = builder.build();

        assertThat(matcher.match("/priority/static"))
            .isEqualTo(match("/priority/static", "static"));
        assertThat(matcher.match("/priority/value"))
            .isEqualTo(match("/priority/*", Map.of("*", "value"), "wildcard"));
        assertThat(matcher.match("/priority/value/tail"))
            .isEqualTo(match("/priority/*", Map.of("*", "value/tail"), "wildcard"));
        assertThat(matcher.match("/priority/value/other"))
            .isEqualTo(match("/priority/*", Map.of("*", "value/other"), "wildcard"));
        assertThat(matcher.match("/specific/static/value/tail"))
            .isEqualTo(match(
                "/specific/static/{id}/tail", Map.of("id", "value"), "longer-stem"
            ));
        assertThat(matcher.match("/fallback/static/value/other"))
            .isEqualTo(match("/fallback/*", Map.of("*", "static/value/other"), "fallback"));
        assertThat(matcher.match("/branch/static/fixed"))
            .isEqualTo(match("/branch/static/fixed", "static-branch"));
        assertThat(matcher.match("/branch/static/tail"))
            .isEqualTo(match("/branch/{id}/tail", Map.of("id", "static"), "parameter-branch"));
        assertThat(matcher.match("/branch/static/missing")).isNull();
    }

    @ParameterizedTest
    @EnumSource(Implementation.class)
    void doesNotLeakCapturesFromRejectedCandidates(Implementation implementation) {
        var builder = implementation.builder();
        builder.add("/foo/{first}/bar", "first");
        builder.add("/foo/{second}/baz", "second");
        builder.add("/foo/{id}/expected", "suffix");
        var matcher = builder.build();

        assertThat(matcher.match("/foo/value/baz"))
            .isEqualTo(match("/foo/{second}/baz", Map.of("second", "value"), "second"));
        assertThat(matcher.match("/foo/value/other")).isNull();
        assertThat(matcher.match("/foo/value/expected"))
            .isEqualTo(match("/foo/{id}/expected", Map.of("id", "value"), "suffix"));
    }

    @ParameterizedTest
    @EnumSource(Implementation.class)
    void rejectsEmptyParametersAndWrongTrailingSlash(Implementation implementation) {
        var builder = implementation.builder();
        builder.add("/single/{id}", "single");
        builder.add("/trailing/{id}/", "trailing");
        builder.add("/suffix/{id}/expected", "suffix");
        var matcher = builder.build();

        assertThat(matcher.match("/single/")).isNull();
        assertThat(matcher.match("/single/value/")).isNull();
        assertThat(matcher.match("/trailing/value")).isNull();
        assertThat(matcher.match("/trailing/value/"))
            .isEqualTo(match("/trailing/{id}/", Map.of("id", "value"), "trailing"));
        assertThat(matcher.match("/suffix/value/expected/")).isNull();
    }

    @ParameterizedTest
    @EnumSource(Implementation.class)
    void detectsEquivalentTemplatesAndKeepsFirstValue(Implementation implementation) {
        var builder = implementation.builder();

        assertThat(builder.add("/", "root")).isNull();
        assertThat(builder.add("/", "ignored"))
            .isEqualTo(new PathTemplateMatcherTestSupport.Previous("/", "root"));
        assertThat(builder.add("/foo/{first}/items/{item}", "parameter")).isNull();
        assertThat(builder.add("/foo/{renamed}/items/{other}", "ignored"))
            .isEqualTo(new PathTemplateMatcherTestSupport.Previous(
                "/foo/{first}/items/{item}", "parameter"
            ));
        assertThat(builder.add("/foo/{first}/items/{item}/", "trailing")).isNull();
        assertThat(builder.add("/wild/*", "wildcard")).isNull();
        assertThat(builder.add("/wild/*", "ignored"))
            .isEqualTo(new PathTemplateMatcherTestSupport.Previous("/wild/*", "wildcard"));

        var matcher = builder.build();
        assertThat(matcher.match("/foo/a/items/b"))
            .isEqualTo(match(
                "/foo/{first}/items/{item}", Map.of("first", "a", "item", "b"), "parameter"
            ));
    }

    @ParameterizedTest
    @EnumSource(Implementation.class)
    void distinguishesDifferentPathsAndTrailingSlashForms(Implementation implementation) {
        var builder = implementation.builder();

        assertThat(builder.add("/foo", "foo")).isNull();
        assertThat(builder.add("/foo/", "foo-trailing")).isNull();
        assertThat(builder.add("/bar", "bar")).isNull();
        assertThat(builder.add("/parameter/{id}", "parameter")).isNull();
        assertThat(builder.add("/parameter/{name}/", "parameter-trailing")).isNull();
        var matcher = builder.build();

        assertThat(matcher.match("/foo")).isEqualTo(match("/foo", "foo"));
        assertThat(matcher.match("/foo/")).isEqualTo(match("/foo/", "foo-trailing"));
        assertThat(matcher.match("/bar")).isEqualTo(match("/bar", "bar"));
        assertThat(matcher.match("/parameter/value"))
            .isEqualTo(match("/parameter/{id}", Map.of("id", "value"), "parameter"));
        assertThat(matcher.match("/parameter/value/"))
            .isEqualTo(match(
                "/parameter/{name}/", Map.of("name", "value"), "parameter-trailing"
            ));
    }

    @ParameterizedTest
    @EnumSource(Implementation.class)
    void supportsBuilderLookupRemovalCopyAndImmutableSnapshots(Implementation implementation) {
        var source = implementation.builder();
        source.add("/foo", "foo");
        source.add("/bar/{id}", "bar");

        assertThat(source.get("/foo")).isEqualTo("foo");
        assertThat(source.get("/bar/{renamed}")).isEqualTo("bar");
        assertThat(source.get("/missing")).isNull();
        assertThat(source.templates()).containsExactlyInAnyOrder("/foo", "/bar/{id}");

        var target = implementation.builder();
        target.addAll(source);
        var firstSnapshot = target.build();

        target.remove("/missing");
        target.remove("/foo");
        target.add("/baz/*", "baz");
        var secondSnapshot = target.build();

        assertThat(firstSnapshot.match("/foo")).isEqualTo(match("/foo", "foo"));
        assertThat(firstSnapshot.match("/baz/value")).isNull();
        assertThat(secondSnapshot.match("/foo")).isNull();
        assertThat(secondSnapshot.match("/bar/value"))
            .isEqualTo(match("/bar/{id}", Map.of("id", "value"), "bar"));
        assertThat(secondSnapshot.match("/baz/value"))
            .isEqualTo(match("/baz/*", Map.of("*", "value"), "baz"));
    }

    @ParameterizedTest
    @EnumSource(Implementation.class)
    void normalizesTemplateWithoutLeadingSlash(Implementation implementation) {
        var builder = implementation.builder();
        builder.add("foo/{id}", "value");

        assertThat(builder.get("/foo/{renamed}")).isEqualTo("value");
        assertThat(builder.build().match("/foo/42"))
            .isEqualTo(match("/foo/{id}", Map.of("id", "42"), "value"));
    }

    @ParameterizedTest
    @EnumSource(Implementation.class)
    void rejectsNullTemplate(Implementation implementation) {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> implementation.builder().add(null, "value"))
            .withMessage("Path must be specified");
    }

    private static Match match(String template, String value) {
        return match(template, Map.of(), value);
    }

    private static Match match(String template, Map<String, String> parameters, String value) {
        return new Match(template, parameters, value);
    }

    private record Route(String template, String value) {}

    private record Case(String path, Match expected) {}
}
