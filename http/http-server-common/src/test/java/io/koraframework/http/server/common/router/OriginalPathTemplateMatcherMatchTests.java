package io.koraframework.http.server.common.router;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class OriginalPathTemplateMatcherMatchTests {

    @Test
    void rootPathMatch() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/", "value");

        // then
        var match = pathTemplateMatcher.match("/");
        assertThat(match).isNotNull();
        assertThat(match.matchedTemplate()).isEqualTo("/");
        assertThat(match.parameters()).isEmpty();
        assertThat(match.value()).isEqualTo("value");
    }

    @Test
    void samePathMatch() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo", "value");

        // then
        var match = pathTemplateMatcher.match("/foo");
        assertThat(match).isNotNull();
        assertThat(match.matchedTemplate()).isEqualTo("/foo");
        assertThat(match.parameters()).isEmpty();
        assertThat(match.value()).isEqualTo("value");
    }

    @Test
    void samePathTrailingSlashNotMatch() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo", "value");

        // then
        assertThat(pathTemplateMatcher.match("/foo/")).isNull();
    }

    @Test
    void differentPathNotMatch() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo", "value");

        // then
        assertThat(pathTemplateMatcher.match("/bar")).isNull();
    }

    @Test
    void templatePathMatch() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}", "value");

        // then
        var match = pathTemplateMatcher.match("/foo/bar");
        assertThat(match).isNotNull();
        assertThat(match.matchedTemplate()).isEqualTo("/foo/{bar}");
        assertThat(match.parameters()).containsExactly(entry("bar", "bar"));
        assertThat(match.value()).isEqualTo("value");
    }

    @Test
    void templatePathAndRequestTrailingSlashNotMatch() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}", "value");

        // then
        assertThat(pathTemplateMatcher.match("/foo/bar/")).isNull();
    }

    @Test
    void templatePathTrailingSlashMatch() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}/", "value");

        // then
        assertThat(pathTemplateMatcher.match("/foo/bar/")).isNotNull();
    }

    @Test
    void templatePathTrailingSlashAndRequestNotMatch() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}/", "value");

        // then
        assertThat(pathTemplateMatcher.match("/foo/bar")).isNull();
    }

    @Test
    void differentTemplatePathNotMatch() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}", "value");

        // then
        assertThat(pathTemplateMatcher.match("/bar/{foo}")).isNull();
    }

    @Test
    void templatePathAndPathMatch() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}/baz", "value");

        // then
        var match = pathTemplateMatcher.match("/foo/bar/baz");
        assertThat(match).isNotNull();
        assertThat(match.matchedTemplate()).isEqualTo("/foo/{bar}/baz");
        assertThat(match.parameters()).containsExactly(entry("bar", "bar"));
        assertThat(match.value()).isEqualTo("value");
    }

    @Test
    void templatePathAndPathAndRequestTrailingSlashNotMatch() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}/baz", "value");

        // then
        assertThat(pathTemplateMatcher.match("/foo/bar/baz/")).isNull();
    }

    @Test
    void templatePathAndPathTrailingSlashMatch() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}/baz/", "value");

        // then
        assertThat(pathTemplateMatcher.match("/foo/bar/baz/")).isNotNull();
    }

    @Test
    void templatePathAndPathTrailingSlashAndRequestNotMatch() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}/baz/", "value");

        // then
        assertThat(pathTemplateMatcher.match("/foo/bar/baz")).isNull();
    }

    @Test
    void differentTemplatePathAndPathNotMatch() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}/baz", "value");

        // then
        assertThat(pathTemplateMatcher.match("/bar/{foo}/baz")).isNull();
    }

    @Test
    void emptyPathMatchesRootPath() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/", "value");

        // then
        var match = pathTemplateMatcher.match("");
        assertThat(match).isNotNull();
        assertThat(match.matchedTemplate()).isEqualTo("/");
        assertThat(match.value()).isEqualTo("value");
    }

    @Test
    void templatePathWithSeveralParametersMatch() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}/baz/{qux}", "value");

        // then
        var match = pathTemplateMatcher.match("/foo/a/baz/b");
        assertThat(match).isNotNull();
        assertThat(match.matchedTemplate()).isEqualTo("/foo/{bar}/baz/{qux}");
        assertThat(match.parameters()).containsExactly(entry("bar", "a"), entry("qux", "b"));
        assertThat(match.value()).isEqualTo("value");
    }

    @Test
    void templatePathDoesNotMatchEmptyParameter() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}", "value");

        // then
        assertThat(pathTemplateMatcher.match("/foo/")).isNull();
    }

    @Test
    void wildcardPathMatch() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/*", "value");

        // then
        var match = pathTemplateMatcher.match("/foo/bar/baz");
        assertThat(match).isNotNull();
        assertThat(match.matchedTemplate()).isEqualTo("/foo/*");
        assertThat(match.parameters()).containsExactly(entry("*", "bar/baz"));
        assertThat(match.value()).isEqualTo("value");
    }

    @Test
    void wildcardPathMatchesEmptyRemainder() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/*", "value");

        // then
        var match = pathTemplateMatcher.match("/foo/");
        assertThat(match).isNotNull();
        assertThat(match.parameters()).containsExactly(entry("*", ""));
    }

    @Test
    void staticPathWinsOverTemplatePath() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}", "template");
        pathTemplateMatcher.add("/foo/baz", "static");

        // then
        var match = pathTemplateMatcher.match("/foo/baz");
        assertThat(match).isNotNull();
        assertThat(match.matchedTemplate()).isEqualTo("/foo/baz");
        assertThat(match.parameters()).isEmpty();
        assertThat(match.value()).isEqualTo("static");
    }

    @Test
    void longerStemWinsOverShorterWildcard() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/*", "wildcard");
        pathTemplateMatcher.add("/foo/bar/{baz}", "template");

        // then
        var match = pathTemplateMatcher.match("/foo/bar/qux");
        assertThat(match).isNotNull();
        assertThat(match.matchedTemplate()).isEqualTo("/foo/bar/{baz}");
        assertThat(match.parameters()).containsExactly(entry("baz", "qux"));
        assertThat(match.value()).isEqualTo("template");
    }

    @Test
    void parametersDoNotLeakFromFailedCandidateWithSameStem() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}/bar", "first");
        pathTemplateMatcher.add("/foo/{baz}/baz", "second");

        // then
        var match = pathTemplateMatcher.match("/foo/value/baz");
        assertThat(match).isNotNull();
        assertThat(match.matchedTemplate()).isEqualTo("/foo/{baz}/baz");
        assertThat(match.parameters()).containsExactly(entry("baz", "value"));
        assertThat(match.value()).isEqualTo("second");
    }

    @Test
    void fallsBackToShorterStemWhenLongerStemDoesNotMatch() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();
        pathTemplateMatcher.add("/foo/*", "wildcard");
        pathTemplateMatcher.add("/foo/bar/{id}/expected", "template");

        // when
        var match = pathTemplateMatcher.match("/foo/bar/value/other");

        // then
        assertThat(match).isNotNull();
        assertThat(match.matchedTemplate()).isEqualTo("/foo/*");
        assertThat(match.parameters()).containsExactly(entry("*", "bar/value/other"));
        assertThat(match.value()).isEqualTo("wildcard");
    }

    @Test
    void wildcardAfterParameterMatchesRemainingPath() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();
        pathTemplateMatcher.add("/foo/{id}/*", "value");

        // when
        var match = pathTemplateMatcher.match("/foo/42/bar/baz");

        // then
        assertThat(match).isNotNull();
        assertThat(match.parameters()).containsExactly(entry("id", "42"), entry("*", "bar/baz"));
    }

    @Test
    void singleParameterIsCapturedOnlyAfterSuffixMatches() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();
        pathTemplateMatcher.add("/foo/{id}/expected", "value");

        // then
        assertThat(pathTemplateMatcher.match("/foo/42/other")).isNull();
        assertThat(pathTemplateMatcher.match("/foo/42/expected").parameters()).containsExactly(entry("id", "42"));
    }
}
