package io.koraframework.config.common.mapper;

import io.koraframework.config.common.Config;
import io.koraframework.config.common.exception.ConfigValueException;
import io.koraframework.config.common.util.ConfigMappingUtils;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigValueMapperModuleTest {

    private final ConfigValueMapperModule module = new ConfigValueMapperModule() {};

    private final Config empty = ConfigMappingUtils.fromMap(Map.of());

    @Test
    void numberMappersReturnNullForMissingValue() {
        for (var mapper : List.of(
            module.integerConfigValueMapper(),
            module.longConfigValueMapper(),
            module.bigIntegerConfigValueMapper(),
            module.floatConfigValueMapper(),
            module.doubleConfigValueMapper()
        )) {
            assertThat(mapper.map(empty.get("missing"))).isNull();
        }
    }

    @Test
    void mapMapperReturnsNullForMissingValue() {
        var mapper = module.mapConfigValueMapper(module.stringConfigValueMapper(), module.stringConfigValueMapper());

        assertThat(mapper.map(empty.get("missing"))).isNull();
    }

    @Test
    void mapMapperKeepsNullEntryForOptionalMissingReference() {
        var config = ConfigMappingUtils.fromMap(Map.of("limits", Map.of("a", "${?CONFIG_VALUE_MAPPER_MODULE_TEST_MISSING}", "b", 5))).resolve();
        var mapper = module.mapConfigValueMapper(module.stringConfigValueMapper(), module.integerConfigValueMapper());

        var expected = new HashMap<String, Integer>();
        expected.put("a", null);
        expected.put("b", 5);
        assertThat(mapper.map(config.get("limits"))).isEqualTo(expected);
    }

    @Test
    void invalidValuesFailWithConfigValueExceptionWithPath() {
        var config = ConfigMappingUtils.fromMap(Map.of("svc", Map.ofEntries(
            Map.entry("int", 1.5),
            Map.entry("long", 1.5),
            Map.entry("uuid", "not-a-uuid"),
            Map.entry("size", "10 megs"),
            Map.entry("pattern", "("),
            Map.entry("localDate", "not-a-date"),
            Map.entry("localTime", "not-a-time"),
            Map.entry("localDateTime", "not-a-date-time"),
            Map.entry("offsetTime", "not-a-time"),
            Map.entry("offsetDateTime", "not-a-date-time")
        )));

        assertInvalid(module.integerConfigValueMapper(), config, "svc.int");
        assertInvalid(module.longConfigValueMapper(), config, "svc.long");
        assertInvalid(module.uuidConfigValueMapper(), config, "svc.uuid");
        assertInvalid(module.sizeConfigValueMapper(), config, "svc.size");
        assertInvalid(module.patternConfigValueMapper(), config, "svc.pattern");
        assertInvalid(module.localDateConfigValueMapper(), config, "svc.localDate");
        assertInvalid(module.localTimeConfigValueMapper(), config, "svc.localTime");
        assertInvalid(module.localDateTimeConfigValueMapper(), config, "svc.localDateTime");
        assertInvalid(module.offsetTimeConfigValueMapper(), config, "svc.offsetTime");
        assertInvalid(module.offsetDateTimeConfigValueMapper(), config, "svc.offsetDateTime");
    }

    private static void assertInvalid(ConfigValueMapper<?> mapper, Config config, String path) {
        assertThatThrownBy(() -> mapper.map(config.get(path)))
            .isInstanceOf(ConfigValueException.class)
            .hasMessageContaining(path);
    }
}
