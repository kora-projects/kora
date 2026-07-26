package io.koraframework.http.server.common.router;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PathTemplateMatcherWildcardTests {

    @Test
    void finalWildcardWithLiteralPrefixMatchesConsistently() {
        for (var matcher : finalWildcardMatchers()) {
            matcher.add.accept("/foo/{id}/aaa*", "aaa");
            matcher.add.accept("/foo/{id}/bbb*", "bbb");

            assertThat(matcher.match.apply("/foo/value/bbb-tail"))
                .as("%s must skip a higher-priority wildcard whose literal prefix does not match", matcher.name)
                .isEqualTo(new Result("/foo/{id}/bbb*", Map.of("id", "value", "*", "-tail"), "bbb"));
            assertThat(matcher.match.apply("/foo/value/ccc-tail"))
                .as("%s must reject all non-matching wildcard prefixes", matcher.name)
                .isNull();
        }
    }

    @Test
    void finalWildcardWorksWithAndWithoutParameters() {
        for (var matcher : finalWildcardMatchers()) {
            matcher.add.accept("/assets*", "assets");
            matcher.add.accept("/tenant/{id}/*", "tenant");

            assertThat(matcher.match.apply("/assets"))
                .as("%s prefix wildcard with empty remainder", matcher.name)
                .isEqualTo(new Result("/assets*", Map.of("*", ""), "assets"));
            assertThat(matcher.match.apply("/assets/css/app.css"))
                .as("%s prefix wildcard with non-empty remainder", matcher.name)
                .isEqualTo(new Result("/assets*", Map.of("*", "/css/app.css"), "assets"));
            assertThat(matcher.match.apply("/tenant/acme/"))
                .as("%s segment wildcard with empty remainder", matcher.name)
                .isEqualTo(new Result("/tenant/{id}/*", Map.of("id", "acme", "*", ""), "tenant"));
            assertThat(matcher.match.apply("/tenant/acme/files/logo.svg"))
                .as("%s segment wildcard with non-empty remainder", matcher.name)
                .isEqualTo(new Result(
                    "/tenant/{id}/*",
                    Map.of("id", "acme", "*", "files/logo.svg"),
                    "tenant"
                ));
        }
    }

    @Test
    void originalAndNewKeepLegacyWildcardInterpretation() {
        for (var matcher : legacyMatchers()) {
            matcher.add.accept("/foo/{id}/aaa*", "aaa");
            matcher.add.accept("/foo/{id}/bbb*", "bbb");

            assertThat(matcher.match.apply("/foo/value/bbb*"))
                .as("%s treats '*' after a parameter as a literal character", matcher.name)
                .isEqualTo(new Result("/foo/{id}/bbb*", Map.of("id", "value"), "bbb"));
            assertThat(matcher.match.apply("/foo/value/bbb-tail"))
                .as("%s retains legacy behavior rather than the new final-wildcard grammar", matcher.name)
                .isNull();
        }
    }

    @Test
    void wildcardMustBeUniqueAndFinal() {
        var invalidTemplates = List.of(
            "/foo/*/bar",
            "/foo*bar",
            "/foo/**",
            "/foo/{id}/asset-*/detail",
            "/foo/{*}/bar"
        );

        for (var template : invalidTemplates) {
            assertThatIllegalArgumentException()
                .as("Radix must reject %s", template)
                .isThrownBy(() -> RadixPathTemplateMatcher.builder().add(template, "value"))
                .withMessageContaining("Wildcard '*' is only allowed once and as the final character");
            assertThatIllegalArgumentException()
                .as("Hybrid must reject %s", template)
                .isThrownBy(() -> HybridPathTemplateMatcher.builder().add(template, "value"))
                .withMessageContaining("Wildcard '*' is only allowed once and as the final character");
            assertThatIllegalArgumentException()
                .as("Decision must reject %s", template)
                .isThrownBy(() -> new DecisionPathTemplateMatcher<>().add(template, "value"))
                .withMessageContaining("Wildcard '*' is only allowed once and as the final character");
        }
    }

    private static List<Matcher> finalWildcardMatchers() {
        var radix = RadixPathTemplateMatcher.<String>builder();
        var hybrid = HybridPathTemplateMatcher.<String>builder();
        var decision = new DecisionPathTemplateMatcher<String>();
        return List.of(
            new Matcher("Radix", radix::add, path -> result(radix.build().match(path))),
            new Matcher("Hybrid", hybrid::add, path -> result(hybrid.build().match(path))),
            new Matcher("Decision", decision::add, path -> result(decision.match(path)))
        );
    }

    private static List<Matcher> legacyMatchers() {
        var original = new OriginalPathTemplateMatcher<String>();
        var newer = new OptimizedOriginalPathTemplateMatcher<String>();
        return List.of(
            new Matcher("Original", original::add, path -> result(original.match(path))),
            new Matcher("New", newer::add, path -> result(newer.match(path)))
        );
    }

    private static Result result(OriginalPathTemplateMatcher.PathTemplateMatch<String> match) {
        return match == null ? null : new Result(match.matchedTemplate(), match.parameters(), match.value());
    }

    private static Result result(OptimizedOriginalPathTemplateMatcher.PathTemplateMatch<String> match) {
        return match == null ? null : new Result(match.matchedTemplate(), match.parameters(), match.value());
    }

    private static Result result(RadixPathTemplateMatcher.PathTemplateMatch<String> match) {
        return match == null ? null : new Result(match.matchedTemplate(), match.parameters(), match.value());
    }

    private static Result result(HybridPathTemplateMatcher.PathTemplateMatch<String> match) {
        return match == null ? null : new Result(match.matchedTemplate(), match.parameters(), match.value());
    }

    private static Result result(DecisionPathTemplateMatcher.PathTemplateMatch<String> match) {
        return match == null ? null : new Result(match.matchedTemplate(), match.parameters(), match.value());
    }

    private record Matcher(String name, BiConsumer<String, String> add, Function<String, Result> match) {}

    private record Result(String template, Map<String, String> parameters, String value) {}
}
