package ru.tinkoff.kora.http.server.common.router;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PathTemplateMatcherMatchTests {

    @Test
    void rootPathMatch() {
        // given
        final PathTemplateMatcher<String> pathTemplateMatcher = new PathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/", "value");

        // then
        assertThat(pathTemplateMatcher.match("/")).isNotNull();
    }

    @Test
    void samePathMatch() {
        // given
        final PathTemplateMatcher<String> pathTemplateMatcher = new PathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo", "value");

        // then
        assertThat(pathTemplateMatcher.match("/foo")).isNotNull();
    }

    @Test
    void samePathTrailingSlashNotMatch() {
        // given
        final PathTemplateMatcher<String> pathTemplateMatcher = new PathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo", "value");

        // then
        assertThat(pathTemplateMatcher.match("/foo/")).isNull();
    }

    @Test
    void differentPathNotMatch() {
        // given
        final PathTemplateMatcher<String> pathTemplateMatcher = new PathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo", "value");

        // then
        assertThat(pathTemplateMatcher.match("/bar")).isNull();
    }

    @Test
    void templatePathMatch() {
        // given
        final PathTemplateMatcher<String> pathTemplateMatcher = new PathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}", "value");

        // then
        assertThat(pathTemplateMatcher.match("/foo/bar")).isNotNull();
    }

    @Test
    void templatePathAndRequestTrailingSlashNotMatch() {
        // given
        final PathTemplateMatcher<String> pathTemplateMatcher = new PathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}", "value");

        // then
        assertThat(pathTemplateMatcher.match("/foo/bar/")).isNull();
    }

    @Test
    void templatePathTrailingSlashMatch() {
        // given
        final PathTemplateMatcher<String> pathTemplateMatcher = new PathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}/", "value");

        // then
        assertThat(pathTemplateMatcher.match("/foo/bar/")).isNotNull();
    }

    @Test
    void templatePathTrailingSlashAndRequestNotMatch() {
        // given
        final PathTemplateMatcher<String> pathTemplateMatcher = new PathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}/", "value");

        // then
        assertThat(pathTemplateMatcher.match("/foo/bar")).isNull();
    }

    @Test
    void differentTemplatePathNotMatch() {
        // given
        final PathTemplateMatcher<String> pathTemplateMatcher = new PathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}", "value");

        // then
        assertThat(pathTemplateMatcher.match("/bar/{foo}")).isNull();
    }

    @Test
    void templatePathAndPathMatch() {
        // given
        final PathTemplateMatcher<String> pathTemplateMatcher = new PathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}/baz", "value");

        // then
        assertThat(pathTemplateMatcher.match("/foo/bar/baz")).isNotNull();
    }

    @Test
    void templatePathAndPathAndRequestTrailingSlashNotMatch() {
        // given
        final PathTemplateMatcher<String> pathTemplateMatcher = new PathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}/baz", "value");

        // then
        assertThat(pathTemplateMatcher.match("/foo/bar/baz/")).isNull();
    }

    @Test
    void templatePathAndPathTrailingSlashMatch() {
        // given
        final PathTemplateMatcher<String> pathTemplateMatcher = new PathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}/baz/", "value");

        // then
        assertThat(pathTemplateMatcher.match("/foo/bar/baz/")).isNotNull();
    }

    @Test
    void templatePathAndPathTrailingSlashAndRequestNotMatch() {
        // given
        final PathTemplateMatcher<String> pathTemplateMatcher = new PathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}/baz/", "value");

        // then
        assertThat(pathTemplateMatcher.match("/foo/bar/baz")).isNull();
    }

    @Test
    void differentTemplatePathAndPathNotMatch() {
        // given
        final PathTemplateMatcher<String> pathTemplateMatcher = new PathTemplateMatcher<>();

        // when
        pathTemplateMatcher.add("/foo/{bar}/baz", "value");

        // then
        assertThat(pathTemplateMatcher.match("/bar/{foo}/baz")).isNull();
    }
}
