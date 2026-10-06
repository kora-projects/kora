package io.koraframework.http.common;

import org.junit.jupiter.api.Test;
import io.koraframework.http.common.header.HttpHeaders;
import io.koraframework.http.common.header.HttpHeadersImpl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    @Test
    void ofMapCopiesValuesTest() {
        var headers = HttpHeaders.of(Map.of("X-Trace", List.of("1")));

        assertThat(headers.add("x-trace", "2").getAll("x-trace"))
            .containsExactly("1", "2");
    }

    @Test
    void ofMapMergesDuplicateNamesTest() {
        var source = new LinkedHashMap<String, List<String>>();
        source.put("Accept", List.of("a"));
        source.put("accept", List.of("b"));

        var headers = HttpHeaders.of(source);

        assertThat(headers.getAll("accept")).containsExactly("a", "b");
    }

    @Test
    void ofPlainMapMergesDuplicateNamesTest() {
        var source = new LinkedHashMap<String, String>();
        source.put("Accept", "a");
        source.put("accept", "b");

        var headers = HttpHeaders.ofPlain(source);

        assertThat(headers.getAll("accept")).containsExactly("a", "b");
    }

    @Test
    void ofEntriesMergesDuplicateNamesTest() {
        var headers = HttpHeaders.of(Map.entry("Accept", List.of("a")), Map.entry("accept", List.of("b")));

        assertThat(headers.getAll("accept")).containsExactly("a", "b");
    }

    @Test
    void ofPlainEntriesMergesDuplicateNamesTest() {
        var headers = HttpHeaders.ofPlain(Map.entry("Set-Cookie", "a=1"), Map.entry("Set-Cookie", "b=2"));

        assertThat(headers.getAll("set-cookie")).containsExactly("a=1", "b=2");
    }

    @Test
    void copyDoesNotShareValuesTest() {
        var original = HttpHeaders.of("X-Trace", "1");

        new HttpHeadersImpl(original).add("x-trace", "2");

        assertThat(original.getAll("x-trace")).containsExactly("1");
    }

}
