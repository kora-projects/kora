package ru.tinkoff.kora.logging.common;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.config.common.factory.MapConfigFactory;

import java.util.Map;

class LoggingConfigValueExtractorTest {

    @Test
    void testParseConfig() {
        var config = MapConfigFactory.fromMap(Map.of(
            "logging", Map.of(
                "level", Map.of(
                    "root", "info",
                    "ru.tinkoff.package1", "debug",
                    "ru.tinkoff.package2", "trace",
                    "ru.tinkoff.package3", "warn",
                    "ru.tinkoff.package4", "error",
                    "ru.tinkoff.package5", "all"
                ))
        ));

        var extractor = new LoggingConfigValueExtractor();

        var result = extractor.extract(config.get("logging"));


        Assertions.assertThat(result.levels())
            .containsEntry("root", "info")
            .containsEntry("ru.tinkoff.package1", "debug")
            .containsEntry("ru.tinkoff.package2", "trace")
            .containsEntry("ru.tinkoff.package3", "warn")
            .containsEntry("ru.tinkoff.package4", "error")
            .containsEntry("ru.tinkoff.package5", "all")
        ;
    }
}
