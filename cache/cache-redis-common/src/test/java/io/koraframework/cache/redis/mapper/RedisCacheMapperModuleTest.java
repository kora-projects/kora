package io.koraframework.cache.redis.mapper;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RedisCacheMapperModuleTest {

    private final RedisCacheMapperModule module = new RedisCacheMapperModule() {};

    @ParameterizedTest
    @ValueSource(strings = {"1.50", "100.00", "1E+3"})
    void bigDecimalValueMapperKeepsScale(String value) {
        var mapper = module.bigDecimalRedisCacheValueMapper();
        var original = new BigDecimal(value);

        var restored = mapper.read(mapper.write(original));

        assertThat(restored).isEqualTo(original);
        assertThat(restored.scale()).isEqualTo(original.scale());
    }
}
