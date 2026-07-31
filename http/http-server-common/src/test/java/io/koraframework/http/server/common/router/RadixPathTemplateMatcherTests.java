package io.koraframework.http.server.common.router;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RadixPathTemplateMatcherTests {

    @Test
    void compressedTrieUsesFarFewerNodesThanCharacterTrie() {
        var builder = RadixPathTemplateMatcher.<Integer>builder();
        for (int i = 0; i < 1024; i++) {
            builder.add("/api/users/" + i + "/{id}", i);
            builder.add("/api/projects/" + i + "/{projectId}/items/{itemId}", i);
            builder.add("/api/files/" + i + "/*", i);
        }
        var matcher = builder.build();

        assertThat(matcher.radixNodeCount()).isLessThan(4096);
        assertThat(matcher.match("/api/users/1023/42").value()).isEqualTo(1023);
        assertThat(matcher.match("/api/projects/1023/project/items/item").value()).isEqualTo(1023);
        assertThat(matcher.match("/api/files/1023/assets/logo.svg").value()).isEqualTo(1023);
        assertThat(matcher.match("/api/missing/1023/42")).isNull();
    }
}
