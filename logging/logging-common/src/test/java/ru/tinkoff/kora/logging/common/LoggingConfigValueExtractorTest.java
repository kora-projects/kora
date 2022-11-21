package ru.tinkoff.kora.logging.common;

import com.typesafe.config.ConfigFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class LoggingConfigValueExtractorTest {

    @Test
    void testParseConfig() {
        var config = ConfigFactory.parseString("""
            logging.level {
              root: info
              ru.tinkoff.package1: debug
              ru.tinkoff.package2: trace
              ru.tinkoff.package3: warn
              ru.tinkoff.package4: error
              ru.tinkoff.package5: all
            }
            """);
        var extractor = new LoggingConfigValueExtractor();

        var result = extractor.extract(config.getObject("logging"));


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
