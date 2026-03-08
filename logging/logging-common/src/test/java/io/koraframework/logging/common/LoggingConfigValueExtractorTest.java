package io.koraframework.logging.common;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import io.koraframework.config.common.factory.MapConfigFactory;

import java.util.Map;

class LoggingConfigValueExtractorTest {

    @Test
    void testParseConfig() {
        var config = MapConfigFactory.fromMap(Map.of(
            "logging", Map.of(
                "level", Map.of(
                    "root", "info",
                    "io.koraframework.package1", "debug",
                    "io.koraframework.package2", "trace",
                    "io.koraframework.package3", "warn",
                    "io.koraframework.package4", "error",
                    "io.koraframework.package5", "all"
                ))
        ));

        var extractor = new LoggingConfigValueExtractor();

        var result = extractor.extract(config.get("logging"));


        Assertions.assertThat(result.levels())
            .containsEntry("root", "info")
            .containsEntry("io.koraframework.package1", "debug")
            .containsEntry("io.koraframework.package2", "trace")
            .containsEntry("io.koraframework.package3", "warn")
            .containsEntry("io.koraframework.package4", "error")
            .containsEntry("io.koraframework.package5", "all")
        ;
    }
}
