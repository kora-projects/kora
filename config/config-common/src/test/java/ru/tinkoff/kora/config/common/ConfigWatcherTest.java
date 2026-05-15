package ru.tinkoff.kora.config.common;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.config.common.factory.MapConfigFactory;
import ru.tinkoff.kora.config.common.origin.FileConfigOrigin;
import ru.tinkoff.kora.config.common.origin.SimpleContainerConfigOrigin;
import ru.tinkoff.kora.config.common.origin.SimpleConfigOrigin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.tinkoff.kora.config.common.ConfigTestUtils.*;


class ConfigWatcherTest {
    private final Path configDir = createConfigDir();
    private Path currentConfigDir = createCurrentDataDir(this.configDir, """
        database.username=test_user
        database.password=test_password
        """);
    private Path dataDir = createOrUpdateDataDir(this.configDir, this.currentConfigDir);
    private Path configFile = createConfigFile(this.configDir, this.dataDir);
    private final ValueOf<Config> config = getConfig();
    private final ConfigWatcher configWatcher = new ConfigWatcher(Optional.of(config.map(Config::origin)), 50);

    @BeforeEach
    void setUp() throws InterruptedException {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(ConfigWatcher.class).setLevel(Level.TRACE);
        System.setProperty("config.file", configFile.toString());
        this.configWatcher.init();
        Thread.sleep(100);
    }

    @AfterEach
    void tearDown() {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(ConfigWatcher.class).setLevel(null);
        this.configWatcher.release();
        System.clearProperty("config.file");
    }

    @Test
    void configRefreshesOnNewDataDir() throws IOException {
        var oldConfig = config.get();
        this.currentConfigDir = createCurrentDataDir(this.configDir, """
            database.username=test_user1
            database.password=test_password
            """);
        this.dataDir = createOrUpdateDataDir(this.configDir, this.currentConfigDir);

        assertWithTimeout(Duration.ofSeconds(10), () -> {
            assertThat(oldConfig).isNotSameAs(config.get());
            assertThat(config.get().get("database.username").asString()).isEqualTo("test_user1");
            assertThat(config.get().get("database.password").asString()).isEqualTo("test_password");
        });
    }

    @Test
    void configRefreshesOnSymlinkChange() throws IOException {
        var oldConfig = config.get();
        var oldConfigDir = this.currentConfigDir;
        this.currentConfigDir = createCurrentDataDir(this.configDir, """
            database.username=test_user1
            database.password=test_password
            """);
        var oldConfigFile = this.configFile.toAbsolutePath().toRealPath();
        this.configFile = createConfigFile(this.configDir, this.currentConfigDir);
        Files.deleteIfExists(oldConfigFile);
        Files.deleteIfExists(oldConfigDir);

        assertWithTimeout(Duration.ofSeconds(10), () -> {
            assertThat(oldConfig).isNotSameAs(config.get());
            assertThat(config.get().get("database.username").asString()).isEqualTo("test_user1");
            assertThat(config.get().get("database.password").asString()).isEqualTo("test_password");
        });
    }

    @Test
    void configRefreshesOnFileChange() throws IOException, InterruptedException {
        var oldConfig = config.get();
        Thread.sleep(10);
        Files.writeString(this.configFile, """
            database.username=test_user1
            database.password=test_password
            """);

        assertWithTimeout(Duration.ofSeconds(10), () -> {
            assertThat(oldConfig).isNotSameAs(config.get());
            assertThat(config.get().get("database.username").asString()).isEqualTo("test_user1");
            assertThat(config.get().get("database.password").asString()).isEqualTo("test_password");
        });
    }

    @Test
    void configRefreshesOnIncludedFileChange() throws IOException, InterruptedException {
        // Create include file
        var includeFile = Files.createTempFile(configDir, "include-", ".conf");
        includeFile.toFile().deleteOnExit();
        Files.writeString(includeFile, """
            database.pool=5
            """);

        var includeConfig = new ValueOf<Config>() {
            private volatile Config config = this.load();

            @Override
            public Config get() {
                return config;
            }

            @Override
            public void refresh() {
                this.config = this.load();
            }

            private Config load() {
                try {
                    var origin = new SimpleContainerConfigOrigin(
                        new FileConfigOrigin(ConfigWatcherTest.this.configFile),
                        new FileConfigOrigin(includeFile)
                    );
                    var properties = new java.util.Properties();
                    try (var is = Files.newInputStream(ConfigWatcherTest.this.configFile)) {
                        properties.load(is);
                    }
                    try (var is = Files.newInputStream(includeFile)) {
                        var includeProps = new java.util.Properties();
                        includeProps.load(is);
                        properties.putAll(includeProps);
                    }
                    return MapConfigFactory.fromProperties(origin, properties);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        var includeWatcher = new ConfigWatcher(Optional.of(includeConfig.map(Config::origin)), 50);
        includeWatcher.init();
        Thread.sleep(100);

        try {
            var oldConfig = includeConfig.get();
            Thread.sleep(10);

            // Modify the include file
            Files.writeString(includeFile, """
                database.pool=20
                """);

            assertWithTimeout(Duration.ofSeconds(10), () -> {
                assertThat(oldConfig).isNotSameAs(includeConfig.get());
                assertThat(includeConfig.get().get("database.pool").asString()).isEqualTo("20");
            });
        } finally {
            includeWatcher.release();
        }
    }

    private static void assertWithTimeout(Duration duration, Runnable runnable) {
        var deadline = Instant.now().plus(duration);
        AssertionError error = null;

        while (Instant.now().isBefore(deadline)) {
            try {
                runnable.run();
                return;
            } catch (AssertionError e) {
                error = e;
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
            }
        }
        throw error;
    }


    ConfigWatcherTest() throws IOException {
    }

    private ValueOf<Config> getConfig() throws IOException {
        return new ValueOf<>() {
            private volatile Config config = this.load();

            @Override
            public Config get() {
                return config;
            }

            @Override
            public void refresh() {
                try {
                    this.config = this.load();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            private Config load() throws IOException {
                var origin = new SimpleContainerConfigOrigin(
                    new FileConfigOrigin(ConfigWatcherTest.this.configFile),
                    new SimpleConfigOrigin("test")
                );
                var properties = new Properties();
                try (var is = Files.newInputStream(ConfigWatcherTest.this.configFile)) {
                    properties.load(is);
                }
                return MapConfigFactory.fromProperties(origin, properties);
            }
        };
    }
}
