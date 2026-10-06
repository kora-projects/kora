package io.koraframework.cache.redis.mapper;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CollectionRedisCacheKeyMapperTests implements RedisCacheMapperModule {

    private final RedisCacheKeyMapper<java.util.Collection<String>> mapper = collectionRedisCacheKeyMapper(stringRedisCacheKeyMapper());

    @Test
    void listKeepsOrder() {
        assertFalse(Arrays.equals(mapper.apply(List.of("a", "b")), mapper.apply(List.of("b", "a"))),
            "[a, b] and [b, a] must not share a cache key");
    }

    @Test
    void itemWithDelimiterDoesNotCollide() {
        assertFalse(Arrays.equals(mapper.apply(List.of("a:b")), mapper.apply(List.of("a", "b"))),
            "[a:b] and [a, b] must not share a cache key");
    }

    @Test
    void setIgnoresIterationOrder() {
        var ab = new LinkedHashSet<>(List.of("a", "b"));
        var ba = new LinkedHashSet<>(List.of("b", "a"));
        assertArrayEquals(mapper.apply(ab), mapper.apply(ba));
    }
}
