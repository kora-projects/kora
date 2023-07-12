package ru.tinkoff.kora.http.server.common.router;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PathTemplateMatcherAddTests {

    @Test
    void rootPathMatch() {
        // given
        final PathTemplateMatcher<String> pathTemplateMatcher = new PathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/", "value");

        // then
        assertThat(pathTemplateMatcher.add("/", "value")).isNotNull();
    }

    @Test
    void samePathMatch() {
        // given
        final PathTemplateMatcher<String> pathTemplateMatcher = new PathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo", "value");

        // then
        assertThat(pathTemplateMatcher.add("/foo", "value")).isNotNull();
    }

    @Test
    void samePathTrailingSlashNotMatch() {
        // given
        final PathTemplateMatcher<String> pathTemplateMatcher = new PathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo", "value");

        // then
        assertThat(pathTemplateMatcher.add("/foo/", "value")).isNull();
    }

    @Test
    void differentPathNotMatch() {
        // given
        final PathTemplateMatcher<String> pathTemplateMatcher = new PathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo", "value");

        // then
        assertThat(pathTemplateMatcher.add("/bar", "value")).isNull();
    }

    @Test
    void templatePathMatch() {
        // given
        final PathTemplateMatcher<String> pathTemplateMatcher = new PathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}", "value");

        // then
        assertThat(pathTemplateMatcher.add("/foo/{bar}", "value")).isNotNull();
    }

    @Test
    void templatePathAndRequestTrailingSlashNotMatch() {
        // given
        final PathTemplateMatcher<String> pathTemplateMatcher = new PathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}", "value");

        // then
        assertThat(pathTemplateMatcher.add("/foo/{bar}/", "value")).isNull();
    }

    @Test
    void templatePathTrailingSlashMatch() {
        // given
        final PathTemplateMatcher<String> pathTemplateMatcher = new PathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}/", "value");

        // then
        assertThat(pathTemplateMatcher.add("/foo/{bar}/", "value")).isNotNull();
    }

    @Test
    void templatePathTrailingSlashAndRequestNotMatch() {
        // given
        final PathTemplateMatcher<String> pathTemplateMatcher = new PathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}/", "value");

        // then
        assertThat(pathTemplateMatcher.add("/foo/{bar}", "value")).isNull();
    }

    @Test
    void differentTemplatePathNotMatch() {
        // given
        final PathTemplateMatcher<String> pathTemplateMatcher = new PathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}", "value");

        // then
        assertThat(pathTemplateMatcher.add("/bar/{foo}", "value")).isNull();
    }

    @Test
    void templatePathAndPathMatch() {
        // given
        final PathTemplateMatcher<String> pathTemplateMatcher = new PathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}/baz", "value");

        // then
        assertThat(pathTemplateMatcher.add("/foo/{bar}/baz", "value")).isNotNull();
    }

    @Test
    void templatePathAndPathAndRequestTrailingSlashNotMatch() {
        // given
        final PathTemplateMatcher<String> pathTemplateMatcher = new PathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}/baz", "value");

        // then
        assertThat(pathTemplateMatcher.add("/foo/{bar}/baz/", "value")).isNull();
    }

    @Test
    void templatePathAndPathTrailingSlashMatch() {
        // given
        final PathTemplateMatcher<String> pathTemplateMatcher = new PathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}/baz/", "value");

        // then
        assertThat(pathTemplateMatcher.add("/foo/{bar}/baz/", "value")).isNotNull();
    }

    @Test
    void templatePathAndPathTrailingSlashAndRequestNotMatch() {
        // given
        final PathTemplateMatcher<String> pathTemplateMatcher = new PathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}/baz/", "value");

        // then
        assertThat(pathTemplateMatcher.add("/foo/{bar}/baz", "value")).isNull();
    }

    @Test
    void differentTemplatePathAndPathNotMatch() {
        // given
        final PathTemplateMatcher<String> pathTemplateMatcher = new PathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}/baz", "value");

        // then
        assertThat(pathTemplateMatcher.add("/bar/{foo}/baz", "value")).isNull();
    }
}
