package ru.tinkoff.kora.http.common;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HttpHeadersImplTest {

    @Test
    void lowerCaseTest() {
        var headers = HttpHeaders.of("Some-Key", "Some-Value");

        assertThat(headers.iterator().next().getKey()).isEqualTo("some-key");
    }

    @Test
    void namesTest() {
        var headers = HttpHeaders.of(
            "test-header-1", "test-value-1",
            "test-header-2", "test-value-2",
            "test-header-3", "test-value-3"
        );

        Set<String> names = headers.names();
        assertThat(names.size()).isEqualTo(3);
        assertThat(names.contains("test-header-1")).isTrue();
        assertThat(names.contains("test-header-2")).isTrue();
        assertThat(names.contains("test-header-3")).isTrue();
    }

    @Test
    void setTest() {
        var headers = HttpHeaders.of(
            "test-header-1", "test-value-1",
            "test-header-2", "test-value-2",
            "test-header-3", "test-value-3"
        );

        assertThat(headers.set("test-header-4", "test-value-4").getFirst("test-header-4"))
            .isEqualTo("test-value-4");
    }

    @Test
    void addTest() {
        var headers = HttpHeaders.of(
            "test-header-1", "test-value-1",
            "test-header-2", "test-value-2",
            "test-header-3", "test-value-3"
        );

        assertThat(headers.add("test-header-3", "test-value-test").getAll("test-header-3"))
            .hasSize(2)
            .containsExactly("test-value-3", "test-value-test");
    }

    @Test
    void withoutTest() {
        var headers = HttpHeaders.of(
            "test-header-1", "test-value-1",
            "test-header-2", "test-value-2",
            "test-header-3", "test-value-3"
        );

        assertThat(headers.remove("test-header-2").has("test-header-2")).isFalse();
    }

}
