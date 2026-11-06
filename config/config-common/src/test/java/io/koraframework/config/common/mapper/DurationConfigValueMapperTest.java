package io.koraframework.config.common.mapper;

import io.koraframework.config.common.ConfigValuePath;
import io.koraframework.config.common.util.ConfigMappingUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DurationConfigValueMapperTest {
    private final DurationConfigValueMapper mapper = new DurationConfigValueMapper();

    private Duration map(Object value) {
        var config = ConfigMappingUtils.fromMap(Map.of("duration", value));
        return mapper.map(config.get(ConfigValuePath.root().child("duration")));
    }

    @Test
    void integralNumberIsMilliseconds() {
        assertThat(map(1500)).isEqualTo(Duration.ofMillis(1500));
        assertThat(map(1500L)).isEqualTo(Duration.ofMillis(1500));
    }

    @Test
    void fractionalNumberIsFractionalMilliseconds() {
        assertThat(map(1.5)).isEqualTo(Duration.ofNanos(1_500_000));
        assertThat(map(0.5)).isEqualTo(Duration.ofNanos(500_000));
        assertThat(map(-1.5)).isEqualTo(Duration.ofNanos(-1_500_000));
        assertThat(map(new BigDecimal("0.000001"))).isEqualTo(Duration.ofNanos(1));
        assertThat(map(1.5)).isEqualTo(map("1.5ms"));
    }
}
