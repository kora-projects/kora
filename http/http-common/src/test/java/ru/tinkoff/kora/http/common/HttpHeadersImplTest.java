package ru.tinkoff.kora.http.common;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.mockito.hamcrest.MockitoHamcrest;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpHeadersImplTest {

    @Test
    void lowerCaseTest(){
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
    void withTest() {
        var headers = HttpHeaders.of(
            "test-header-1", "test-value-1",
            "test-header-2", "test-value-2",
            "test-header-3", "test-value-3"
        );

        assertThat(headers.with("test-header-4", "test-value-4").getFirst("test-header-4"))
            .isEqualTo("test-value-4");
    }

    @Test
    void withoutTest() {
        var headers = HttpHeaders.of(
            "test-header-1", "test-value-1",
            "test-header-2", "test-value-2",
            "test-header-3", "test-value-3"
        );

        assertThat(headers.without("test-header-2").has("test-header-2")).isFalse();
    }

}
