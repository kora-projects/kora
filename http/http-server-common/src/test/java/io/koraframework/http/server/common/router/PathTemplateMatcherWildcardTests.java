package io.koraframework.http.server.common.router;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Map;

import static io.koraframework.http.server.common.router.PathTemplateMatcherTestSupport.Implementation;
import static io.koraframework.http.server.common.router.PathTemplateMatcherTestSupport.Match;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PathTemplateMatcherWildcardTests {

    @ParameterizedTest
    @EnumSource(Implementation.class)
    void finalWildcardWithLiteralPrefixSkipsRejectedCandidate(Implementation implementation) {
        var builder = implementation.builder();
        builder.add("/foo/{id}/aaa*", "aaa");
        builder.add("/foo/{id}/bbb*", "bbb");
        var matcher = builder.build();

        assertThat(matcher.match("/foo/value/bbb-tail"))
            .isEqualTo(new Match(
                "/foo/{id}/bbb*",
                Map.of("id", "value", "*", "-tail"),
                "bbb"
            ));
        assertThat(matcher.match("/foo/value/ccc-tail")).isNull();
    }

    @ParameterizedTest
    @EnumSource(Implementation.class)
    void finalWildcardWorksWithAndWithoutParameters(Implementation implementation) {
        var builder = implementation.builder();
        builder.add("/assets*", "assets");
        builder.add("/tenant/{id}/*", "tenant");
        var matcher = builder.build();

        assertThat(matcher.match("/assets"))
            .isEqualTo(new Match("/assets*", Map.of("*", ""), "assets"));
        assertThat(matcher.match("/assets/css/app.css"))
            .isEqualTo(new Match("/assets*", Map.of("*", "/css/app.css"), "assets"));
        assertThat(matcher.match("/tenant/acme/"))
            .isEqualTo(new Match(
                "/tenant/{id}/*", Map.of("id", "acme", "*", ""), "tenant"
            ));
        assertThat(matcher.match("/tenant/acme/files/logo.svg"))
            .isEqualTo(new Match(
                "/tenant/{id}/*",
                Map.of("id", "acme", "*", "files/logo.svg"),
                "tenant"
            ));
    }

    @ParameterizedTest
    @EnumSource(Implementation.class)
    void wildcardMustBeUniqueAndInsideFinalSegment(Implementation implementation) {
        var invalidTemplates = List.of(
            "/foo/*/bar",
            "/foo/**",
            "/foo/a*b*c",
            "/foo/*/",
            "/foo/{id}/asset-*/detail",
            "/foo/{*}/bar",
            "/foo/{*}",
            "/foo/{id*}"
        );

        for (var template : invalidTemplates) {
            assertThatIllegalArgumentException()
                .as("%s must reject %s", implementation, template)
                .isThrownBy(() -> implementation.builder().add(template, "value"))
                .withMessageContaining("Wildcard '*' is only allowed once and in the final path segment")
                .withMessageContaining("Valid examples: /files/* and /files/*.js");
        }
    }

    @ParameterizedTest
    @EnumSource(Implementation.class)
    void wildcardSuffixMatchesAndCapturesWithoutSuffix(Implementation implementation) {
        var builder = implementation.builder();
        assertThat(builder.add("/api/files/*.js", "js")).isNull();
        assertThat(builder.add("/api/files/*.txt", "txt")).isNull();
        assertThat(builder.add("/downloads/archive-*.tar.gz", "archive")).isNull();
        assertThat(builder.add("/foo*bar", "same-segment")).isNull();
        var matcher = builder.build();

        assertThat(matcher.match("/api/files/app.min.js"))
            .isEqualTo(new Match("/api/files/*.js", Map.of("*", "app.min"), "js"));
        assertThat(matcher.match("/api/files/readme.txt"))
            .isEqualTo(new Match("/api/files/*.txt", Map.of("*", "readme"), "txt"));
        assertThat(matcher.match("/api/files/.js"))
            .isEqualTo(new Match("/api/files/*.js", Map.of("*", ""), "js"));
        assertThat(matcher.match("/api/files/nested/app.js"))
            .isEqualTo(new Match("/api/files/*.js", Map.of("*", "nested/app"), "js"));
        assertThat(matcher.match("/downloads/archive-release.tar.gz"))
            .isEqualTo(new Match(
                "/downloads/archive-*.tar.gz", Map.of("*", "release"), "archive"
            ));
        assertThat(matcher.match("/foovaluebar"))
            .isEqualTo(new Match("/foo*bar", Map.of("*", "value"), "same-segment"));

        assertThat(matcher.match("/api/files/app.json")).isNull();
        assertThat(matcher.match("/api/files/app.js.bak")).isNull();
        assertThat(matcher.match("/downloads/release.tar.gz")).isNull();
        assertThat(matcher.match("/foovaluebaz")).isNull();
    }

    @ParameterizedTest
    @EnumSource(Implementation.class)
    void wildcardSuffixWorksAfterParameters(Implementation implementation) {
        var builder = implementation.builder();
        builder.add("/tenant/{tenant}/report-*.json", "report");
        var matcher = builder.build();

        assertThat(matcher.match("/tenant/acme/report-summary.json"))
            .isEqualTo(new Match(
                "/tenant/{tenant}/report-*.json",
                Map.of("tenant", "acme", "*", "summary"),
                "report"
            ));
        assertThat(matcher.match("/tenant/acme/report-.json"))
            .isEqualTo(new Match(
                "/tenant/{tenant}/report-*.json",
                Map.of("tenant", "acme", "*", ""),
                "report"
            ));
        assertThat(matcher.match("/tenant/acme/report-summary.txt")).isNull();
    }

    @ParameterizedTest
    @EnumSource(Implementation.class)
    void suffixWildcardPrecedesBareCatchAll(Implementation implementation) {
        for (boolean suffixesFirst : List.of(false, true)) {
            var builder = implementation.builder();
            if (suffixesFirst) {
                builder.add("/api/files/*.js", "js");
                builder.add("/api/files/*.txt", "txt");
                builder.add("/api/files/*", "all");
            } else {
                builder.add("/api/files/*", "all");
                builder.add("/api/files/*.js", "js");
                builder.add("/api/files/*.txt", "txt");
            }
            var matcher = builder.build();

            assertThat(matcher.match("/api/files/app.js"))
                .as("%s, suffixesFirst=%s", implementation, suffixesFirst)
                .isEqualTo(new Match("/api/files/*.js", Map.of("*", "app"), "js"));
            assertThat(matcher.match("/api/files/readme.txt"))
                .isEqualTo(new Match("/api/files/*.txt", Map.of("*", "readme"), "txt"));
            assertThat(matcher.match("/api/files/image.png"))
                .isEqualTo(new Match("/api/files/*", Map.of("*", "image.png"), "all"));
        }
    }

    @ParameterizedTest
    @EnumSource(Implementation.class)
    void moreSpecificWildcardSuffixWinsRegardlessOfInsertionOrder(Implementation implementation) {
        for (boolean mostSpecificFirst : List.of(false, true)) {
            var builder = implementation.builder();
            if (mostSpecificFirst) {
                builder.add("/api/files/report-*.tar.gz", "prefixed-tar");
                builder.add("/api/files/*.tar.gz", "tar");
                builder.add("/api/files/*.gz", "gz");
            } else {
                builder.add("/api/files/*.gz", "gz");
                builder.add("/api/files/*.tar.gz", "tar");
                builder.add("/api/files/report-*.tar.gz", "prefixed-tar");
            }
            var matcher = builder.build();

            assertThat(matcher.match("/api/files/report-release.tar.gz"))
                .as("%s, mostSpecificFirst=%s", implementation, mostSpecificFirst)
                .isEqualTo(new Match(
                    "/api/files/report-*.tar.gz",
                    Map.of("*", "release"),
                    "prefixed-tar"
                ));
            assertThat(matcher.match("/api/files/archive.tar.gz"))
                .isEqualTo(new Match(
                    "/api/files/*.tar.gz", Map.of("*", "archive"), "tar"
                ));
            assertThat(matcher.match("/api/files/archive.gz"))
                .isEqualTo(new Match("/api/files/*.gz", Map.of("*", "archive"), "gz"));
        }
    }

    @ParameterizedTest
    @EnumSource(Implementation.class)
    void largeWildcardSuffixGroupPreservesPriorityAndFallback(Implementation implementation) {
        var builder = implementation.builder();
        builder.add("/api/files/*", "all");
        builder.add("/api/files/*.gz", "gz");
        builder.add("/api/files/*.tar.gz", "tar");
        for (int i = 0; i < 16; i++) {
            builder.add("/api/files/*.ext%02d".formatted(i), "extension-" + i);
        }
        var matcher = builder.build();

        for (int i = 0; i < 16; i++) {
            assertThat(matcher.match("/api/files/archive.ext%02d".formatted(i)))
                .as("%s extension %s", implementation, i)
                .isEqualTo(new Match(
                    "/api/files/*.ext%02d".formatted(i),
                    Map.of("*", "archive"),
                    "extension-" + i
                ));
        }
        assertThat(matcher.match("/api/files/archive.tar.gz"))
            .isEqualTo(new Match("/api/files/*.tar.gz", Map.of("*", "archive"), "tar"));
        assertThat(matcher.match("/api/files/archive.gz"))
            .isEqualTo(new Match("/api/files/*.gz", Map.of("*", "archive"), "gz"));
        assertThat(matcher.match("/api/files/archive.unknown"))
            .isEqualTo(new Match(
                "/api/files/*", Map.of("*", "archive.unknown"), "all"
            ));
    }

    @ParameterizedTest
    @EnumSource(Implementation.class)
    void suffixWildcardTemplatesRemainDistinctBuilderEntries(Implementation implementation) {
        var builder = implementation.builder();
        assertThat(builder.add("/api/files/*.js", "js")).isNull();
        assertThat(builder.add("/api/files/*.txt", "txt")).isNull();
        assertThat(builder.get("/api/files/*.js")).isEqualTo("js");
        assertThat(builder.get("/api/files/*.txt")).isEqualTo("txt");
        assertThat(builder.templates())
            .containsExactlyInAnyOrder("/api/files/*.js", "/api/files/*.txt");
        assertThat(builder.add("/api/files/*.js", "new-js"))
            .isEqualTo(new PathTemplateMatcherTestSupport.Previous("/api/files/*.js", "js"));
        assertThat(builder.get("/api/files/*.js")).isEqualTo("js");
        assertThat(builder.templates())
            .containsExactlyInAnyOrder("/api/files/*.js", "/api/files/*.txt");

        builder.remove("/api/files/*.js");

        assertThat(builder.get("/api/files/*.js")).isNull();
        assertThat(builder.get("/api/files/*.txt")).isEqualTo("txt");
        assertThat(builder.build().match("/api/files/app.js")).isNull();
    }
}
