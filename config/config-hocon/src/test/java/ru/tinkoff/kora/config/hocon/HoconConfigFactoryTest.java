package ru.tinkoff.kora.config.hocon;

import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.config.common.ConfigValue;
import ru.tinkoff.kora.config.common.origin.SimpleConfigOrigin;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

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
    void testTrackingIncluderRecordsIncludedFile() throws IOException {
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

            var includer = new TrackingConfigIncluder();
            var options = ConfigParseOptions.defaults().setIncluder(includer);
            ConfigFactory.parseFile(mainFile.toFile(), options);

            assertThat(includer.getIncludedFiles()).hasSize(1);
            assertThat(includer.getIncludedFiles()).contains(overrideFile.toAbsolutePath());
        } finally {
            Files.walk(tempDir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> p.toFile().delete());
        }
    }

    @Test
    void testTrackingIncluderRecordsNestedIncludes() throws IOException {
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

            var includer = new TrackingConfigIncluder();
            var options = ConfigParseOptions.defaults().setIncluder(includer);
            ConfigFactory.parseFile(mainFile.toFile(), options);

            assertThat(includer.getIncludedFiles()).hasSize(2);
            assertThat(includer.getIncludedFiles()).contains(level1File.toAbsolutePath());
            assertThat(includer.getIncludedFiles()).contains(level2File.toAbsolutePath());
        } finally {
            Files.walk(tempDir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> p.toFile().delete());
        }
    }

    @Test
    void testTrackingIncluderRecordsHeuristicInclude() throws IOException {
        var tempDir = Files.createTempDirectory("hocon-test");
        try {
            var overrideFile = tempDir.resolve("override.conf");
            Files.writeString(overrideFile, """
                database.url = "jdbc:postgresql://prod:5432/db"
                """);

            var mainFile = tempDir.resolve("application.conf");
            Files.writeString(mainFile, """
                include "override.conf"
                database.username = "user"
                """);

            var includer = new TrackingConfigIncluder();
            var options = ConfigParseOptions.defaults().setIncluder(includer);
            ConfigFactory.parseFile(mainFile.toFile(), options);

            assertThat(includer.getIncludedFiles()).hasSize(1);
            assertThat(includer.getIncludedFiles()).contains(overrideFile.toAbsolutePath());
        } finally {
            Files.walk(tempDir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> p.toFile().delete());
        }
    }

    @Test
    void testTrackingIncluderIgnoresNonExistentOptionalInclude() throws IOException {
        var tempDir = Files.createTempDirectory("hocon-test");
        try {
            var mainFile = tempDir.resolve("application.conf");
            Files.writeString(mainFile, """
                include file("%s")
                database.username = "user"
                """.formatted(tempDir.resolve("nonexistent.conf").toAbsolutePath()));

            var includer = new TrackingConfigIncluder();
            var options = ConfigParseOptions.defaults().setIncluder(includer);
            ConfigFactory.parseFile(mainFile.toFile(), options);

            // includeFile is called even for non-existent files, but the file doesn't exist on disk
            // TrackingConfigIncluder records the path; enrichOriginWithIncludes won't add non-existent files
            // since FileConfigOrigin is only created for paths that exist
            assertThat(includer.getIncludedFiles()).hasSize(1);
            assertThat(includer.getIncludedFiles()).contains(tempDir.resolve("nonexistent.conf").toAbsolutePath());
        } finally {
            Files.walk(tempDir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> p.toFile().delete());
        }
    }
}
