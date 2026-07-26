package io.koraframework.http.server.common.router;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OriginalPathTemplateMatcherAddTests {

    @Test
    void rootPathMatch() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/", "value");

        // then
        var oldValue = pathTemplateMatcher.add("/", "otherValue");
        assertThat(oldValue).isNotNull();
        assertThat(oldValue.getKey().templateString()).isEqualTo("/");
        assertThat(oldValue.getValue()).isEqualTo("value");
        assertThat(pathTemplateMatcher.get("/")).isEqualTo("value");
    }

    @Test
    void samePathMatch() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo", "value");

        // then
        var oldValue = pathTemplateMatcher.add("/foo", "otherValue");
        assertThat(oldValue).isNotNull();
        assertThat(oldValue.getKey().templateString()).isEqualTo("/foo");
        assertThat(oldValue.getValue()).isEqualTo("value");
        assertThat(pathTemplateMatcher.get("/foo")).isEqualTo("value");
    }

    @Test
    void samePathTrailingSlashNotMatch() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo", "value");

        // then
        assertThat(pathTemplateMatcher.add("/foo/", "value")).isNull();
    }

    @Test
    void differentPathNotMatch() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo", "value");

        // then
        assertThat(pathTemplateMatcher.add("/bar", "value")).isNull();
    }

    @Test
    void templatePathMatch() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}", "value");

        // then
        var oldValue = pathTemplateMatcher.add("/foo/{bar}", "otherValue");
        assertThat(oldValue).isNotNull();
        assertThat(oldValue.getKey().templateString()).isEqualTo("/foo/{bar}");
        assertThat(oldValue.getValue()).isEqualTo("value");
    }

    @Test
    void templatePathAndRequestTrailingSlashNotMatch() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}", "value");

        // then
        assertThat(pathTemplateMatcher.add("/foo/{bar}/", "value")).isNull();
    }

    @Test
    void templatePathTrailingSlashMatch() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}/", "value");

        // then
        assertThat(pathTemplateMatcher.add("/foo/{bar}/", "value")).isNotNull();
    }

    @Test
    void templatePathTrailingSlashAndRequestNotMatch() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}/", "value");

        // then
        assertThat(pathTemplateMatcher.add("/foo/{bar}", "value")).isNull();
    }

    @Test
    void differentTemplatePathNotMatch() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}", "value");

        // then
        assertThat(pathTemplateMatcher.add("/bar/{foo}", "value")).isNull();
    }

    @Test
    void templatePathAndPathMatch() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}/baz", "value");

        // then
        var oldValue = pathTemplateMatcher.add("/foo/{bar}/baz", "otherValue");
        assertThat(oldValue).isNotNull();
        assertThat(oldValue.getKey().templateString()).isEqualTo("/foo/{bar}/baz");
        assertThat(oldValue.getValue()).isEqualTo("value");
    }

    @Test
    void templatePathAndPathAndRequestTrailingSlashNotMatch() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}/baz", "value");

        // then
        assertThat(pathTemplateMatcher.add("/foo/{bar}/baz/", "value")).isNull();
    }

    @Test
    void templatePathAndPathTrailingSlashMatch() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}/baz/", "value");

        // then
        assertThat(pathTemplateMatcher.add("/foo/{bar}/baz/", "value")).isNotNull();
    }

    @Test
    void templatePathAndPathTrailingSlashAndRequestNotMatch() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}/baz/", "value");

        // then
        assertThat(pathTemplateMatcher.add("/foo/{bar}/baz", "value")).isNull();
    }

    @Test
    void differentTemplatePathAndPathNotMatch() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}/baz", "value");

        // then
        assertThat(pathTemplateMatcher.add("/bar/{foo}/baz", "value")).isNull();
    }

    @Test
    void sameTemplateShapeWithDifferentParameterNamesReturnsOldValue() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}/baz/{qux}", "value");

        // then
        var oldValue = pathTemplateMatcher.add("/foo/{first}/baz/{second}", "otherValue");
        assertThat(oldValue).isNotNull();
        assertThat(oldValue.getKey().templateString()).isEqualTo("/foo/{bar}/baz/{qux}");
        assertThat(oldValue.getValue()).isEqualTo("value");
        assertThat(pathTemplateMatcher.match("/foo/a/baz/b").value()).isEqualTo("value");
    }

    @Test
    void wildcardDuplicateReturnsOldValue() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/*", "value");

        // then
        var oldValue = pathTemplateMatcher.add("/foo/*", "otherValue");
        assertThat(oldValue).isNotNull();
        assertThat(oldValue.getKey().templateString()).isEqualTo("/foo/*");
        assertThat(oldValue.getValue()).isEqualTo("value");
        assertThat(pathTemplateMatcher.match("/foo/bar/baz").parameters()).containsEntry("*", "bar/baz");
    }

    @Test
    void staticRouteHasPriorityOverWildcard() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/*", "wildcard");
        pathTemplateMatcher.add("/foo/bar", "static");

        // then
        assertThat(pathTemplateMatcher.match("/foo/bar").value()).isEqualTo("static");
        assertThat(pathTemplateMatcher.match("/foo/baz").value()).isEqualTo("wildcard");
    }

    @Test
    void staticRouteHasPriorityOverTemplate() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}", "template");
        pathTemplateMatcher.add("/foo/baz", "static");

        // then
        assertThat(pathTemplateMatcher.match("/foo/baz").value()).isEqualTo("static");
        assertThat(pathTemplateMatcher.match("/foo/qux").value()).isEqualTo("template");
    }

    @Test
    void moreSpecificTemplateHasPriority() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}", "short");
        pathTemplateMatcher.add("/foo/{bar}/baz", "long");

        // then
        assertThat(pathTemplateMatcher.match("/foo/a").value()).isEqualTo("short");
        assertThat(pathTemplateMatcher.match("/foo/a/baz").value()).isEqualTo("long");
    }

    @Test
    void addTemplateWithoutLeadingSlashNormalizesPath() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("foo/{bar}", "value");

        // then
        assertThat(pathTemplateMatcher.match("/foo/baz").value()).isEqualTo("value");
        assertThat(pathTemplateMatcher.get("/foo/{bar}")).isEqualTo("value");
    }

    @Test
    void nullTemplateFailsFast() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // then
        assertThatThrownBy(() -> pathTemplateMatcher.add((String) null, "value"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Path must be specified");
    }

    @Test
    void getReturnsNullWhenTemplateIsAbsent() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo", "value");

        // then
        assertThat(pathTemplateMatcher.get("/bar")).isNull();
    }

    @Test
    void removeDeletesTemplateAndKeepsOtherTemplatesWithSameStem() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();
        pathTemplateMatcher.add("/foo/{bar}", "template");
        pathTemplateMatcher.add("/foo/baz", "static");

        // when
        pathTemplateMatcher.remove("/foo/baz");

        // then
        assertThat(pathTemplateMatcher.match("/foo/baz").value()).isEqualTo("template");
        assertThat(pathTemplateMatcher.get("/foo/baz")).isNull();
        assertThat(pathTemplateMatcher.get("/foo/{bar}")).isEqualTo("template");
    }

    @Test
    void removeMissingTemplateDoesNothing() {
        // given
        final OriginalPathTemplateMatcher<String> pathTemplateMatcher = new OriginalPathTemplateMatcher<>();
        pathTemplateMatcher.add("/foo", "value");

        // when
        pathTemplateMatcher.remove("/bar");

        // then
        assertThat(pathTemplateMatcher.match("/foo").value()).isEqualTo("value");
    }

    @Test
    void addAllCopiesTemplates() {
        // given
        final OriginalPathTemplateMatcher<String> source = new OriginalPathTemplateMatcher<>();
        source.add("/foo", "static");
        source.add("/bar/{baz}", "template");

        final OriginalPathTemplateMatcher<String> target = new OriginalPathTemplateMatcher<>();

        // when
        target.addAll(source);

        // then
        assertThat(target.match("/foo").value()).isEqualTo("static");
        assertThat(target.match("/bar/qux").value()).isEqualTo("template");
        assertThat(target.getPathTemplates())
            .extracting(OriginalPathTemplate::templateString)
            .containsExactlyInAnyOrder("/foo", "/bar/{baz}");
    }
}
