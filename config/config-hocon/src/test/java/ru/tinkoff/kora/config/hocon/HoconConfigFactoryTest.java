package ru.tinkoff.kora.config.hocon;

import com.typesafe.config.ConfigFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.config.common.ConfigValue;
import ru.tinkoff.kora.config.common.origin.SimpleConfigOrigin;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class HoconConfigFactoryTest {

    @Test
    void testFromHocon() {
        var hocon = ConfigFactory.parseString("""
                testObject {
                  f1 = "test1"
                }
                test {
                  f1 = "test1"
                  f2 = prefix_${testObject.f1}_suffix
                  f3 = 10
                  f4 = 15 seconds
                  f5 = true
                }
                testArray = [1, 2, 3]
                """)
            .resolve();

        var config = HoconConfigFactory.fromHocon(new SimpleConfigOrigin(""), hocon);

        assertThat(config.get("testObject")).isInstanceOf(ConfigValue.ObjectValue.class);
        assertThat(config.get("testObject.f1")).isInstanceOf(ConfigValue.StringValue.class)
            .extracting(ConfigValue::asString).isEqualTo("test1");

        assertThat(config.get("test")).isInstanceOf(ConfigValue.ObjectValue.class);
        assertThat(config.get("test.f1")).isInstanceOf(ConfigValue.StringValue.class)
            .extracting(ConfigValue::asString).isEqualTo("test1");
        assertThat(config.get("test.f2")).isInstanceOf(ConfigValue.StringValue.class)
            .extracting(ConfigValue::asString).isEqualTo("prefix_test1_suffix");
        assertThat(config.get("test.f3")).isInstanceOf(ConfigValue.NumberValue.class)
            .extracting(ConfigValue::asNumber).isEqualTo(10);
        assertThat(config.get("test.f4")).isInstanceOf(ConfigValue.StringValue.class)
            .extracting(ConfigValue::asString).isEqualTo("15 seconds");
        assertThat(config.get("test.f5")).isInstanceOf(ConfigValue.BooleanValue.class)
            .extracting(ConfigValue::asBoolean).isEqualTo(true);
        assertThat(config.get("testArray")).isInstanceOf(ConfigValue.ArrayValue.class)
            .extracting(v -> v.asArray().value().stream().map(ConfigValue::value).toList())
            .isEqualTo(List.of(1, 2, 3));
    }

    @Test
    void testExtractFileOrigins() throws IOException {
        var tempDir = Files.createTempDirectory("hocon-test");
        try {
            var overrideFile = tempDir.resolve("override.conf");
            Files.writeString(overrideFile, """
                database.url = "jdbc:postgresql://prod:5432/db"
                """);

            var mainFile = tempDir.resolve("application.conf");
            Files.writeString(mainFile, """
                include file("%s")
                database.username = "user"
                """.formatted(overrideFile.toAbsolutePath()));

            var parsedConfig = ConfigFactory.parseFile(mainFile.toFile());
            var files = HoconConfigModule.extractIncludedFiles(parsedConfig);

            assertThat(files).hasSize(2);
            assertThat(files).contains(mainFile.toAbsolutePath());
            assertThat(files).contains(overrideFile.toAbsolutePath());
        } finally {
            Files.walk(tempDir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> p.toFile().delete());
        }
    }

    @Test
    void testExtractFileOriginsNestedIncludes() throws IOException {
        var tempDir = Files.createTempDirectory("hocon-test");
        try {
            var level2File = tempDir.resolve("level2.conf");
            Files.writeString(level2File, """
                database.pool = 10
                """);

            var level1File = tempDir.resolve("level1.conf");
            Files.writeString(level1File, """
                include file("%s")
                database.url = "jdbc:postgresql://prod:5432/db"
                """.formatted(level2File.toAbsolutePath()));

            var mainFile = tempDir.resolve("application.conf");
            Files.writeString(mainFile, """
                include file("%s")
                database.username = "user"
                """.formatted(level1File.toAbsolutePath()));

            var parsedConfig = ConfigFactory.parseFile(mainFile.toFile());
            var files = HoconConfigModule.extractIncludedFiles(parsedConfig);

            assertThat(files).hasSize(3);
            assertThat(files).contains(mainFile.toAbsolutePath());
            assertThat(files).contains(level1File.toAbsolutePath());
            assertThat(files).contains(level2File.toAbsolutePath());
        } finally {
            Files.walk(tempDir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> p.toFile().delete());
        }
    }

    @Test
    void testExtractFileOriginsWithOptionalNonExistentInclude() throws IOException {
        var tempDir = Files.createTempDirectory("hocon-test");
        try {
            var mainFile = tempDir.resolve("application.conf");
            Files.writeString(mainFile, """
                include file("%s")
                database.username = "user"
                """.formatted(tempDir.resolve("nonexistent.conf").toAbsolutePath()));

            var parsedConfig = ConfigFactory.parseFile(mainFile.toFile());
            var files = HoconConfigModule.extractIncludedFiles(parsedConfig);

            // Non-existent optional include should not appear in result
            assertThat(files).hasSize(1);
            assertThat(files).contains(mainFile.toAbsolutePath());
        } finally {
            Files.walk(tempDir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> p.toFile().delete());
        }
    }
}
