package ru.tinkoff.kora.config.common;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.ValueOf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.tinkoff.kora.config.common.ConfigTestUtils.*;


class ConfigWatcherTest {
    private final Path configDir = createConfigDir();
    private Path currentConfigDir = createCurrentDataDir(this.configDir, """
        database {
            username = "test_user"
            password = "test_password"
        }
        """);
    private Path dataDir = createOrUpdateDataDir(this.configDir, this.currentConfigDir);
    private Path configFile = createConfigFile(this.configDir, this.dataDir);
    private final ValueOf<Config> config = getConfig();
    private final ConfigWatcher configWatcher = new ConfigWatcher(config, 50);

    @BeforeEach
    void setUp() throws InterruptedException {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(ConfigWatcher.class).setLevel(Level.TRACE);
        System.setProperty("config.file", configFile.toString());
        this.configWatcher.init().block();
        Thread.sleep(100);
    }

    @AfterEach
    void tearDown() {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(ConfigWatcher.class).setLevel(null);
        this.configWatcher.release().block();
        System.clearProperty("config.file");
    }

    @Test
    void configRefreshesOnNewDataDir() throws IOException {
        var oldConfig = config.get();
        this.currentConfigDir = createCurrentDataDir(this.configDir, """
            database {
                username = "test_user1"
                password = "test_password"
            }
            """);
        this.dataDir = createOrUpdateDataDir(this.configDir, this.currentConfigDir);

        assertWithTimeout(Duration.ofSeconds(10), () -> {
            assertThat(oldConfig).isNotSameAs(config.get());
            assertThat(config.get().getString("database.username")).isEqualTo("test_user1");
            assertThat(config.get().getString("database.password")).isEqualTo("test_password");
        });
    }

    @Test
    void configRefreshesOnSymlinkChange() throws IOException {
        var oldConfig = config.get();
        var oldConfigDir = this.currentConfigDir;
        this.currentConfigDir = createCurrentDataDir(this.configDir, """
            database {
                username = "test_user1"
                password = "test_password"
            }
            """);
        var oldConfigFile = this.configFile.toAbsolutePath().toRealPath();
        this.configFile = createConfigFile(this.configDir, this.currentConfigDir);
        Files.deleteIfExists(oldConfigFile);
        Files.deleteIfExists(oldConfigDir);

        assertWithTimeout(Duration.ofSeconds(10), () -> {
            assertThat(oldConfig).isNotSameAs(config.get());
            assertThat(config.get().getString("database.username")).isEqualTo("test_user1");
            assertThat(config.get().getString("database.password")).isEqualTo("test_password");
        });
    }

    @Test
    void configRefreshesOnFileChange() throws IOException, InterruptedException {
        var oldConfig = config.get();
        Thread.sleep(10);
        Files.writeString(this.configFile, """
            database {
                username = "test_user1"
                password = "test_password"
            }
            """);

        assertWithTimeout(Duration.ofSeconds(10), () -> {
            assertThat(oldConfig).isNotSameAs(config.get());
            assertThat(config.get().getString("database.username")).isEqualTo("test_user1");
            assertThat(config.get().getString("database.password")).isEqualTo("test_password");
        });
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

    private ValueOf<Config> getConfig() {
        return new ValueOf<>() {
            private volatile Config config = this.load();

            @Override
            public Config get() {
                return config;
            }

            @Override
            public Mono<Void> refresh() {
                return Mono.fromRunnable(() -> this.config = this.load());
            }

            private Config load() {
                return ConfigFactory.parseFile(ConfigWatcherTest.this.configFile.toFile()).withFallback(ConfigFactory.systemProperties());
            }
        };
    }
}
