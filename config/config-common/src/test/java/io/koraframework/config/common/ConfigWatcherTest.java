package io.koraframework.config.common;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import io.koraframework.application.graph.ApplicationGraphDraw;
import io.koraframework.application.graph.InitializedGraph;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.config.common.factory.MapConfigFactory;
import io.koraframework.config.common.origin.ConfigOrigin;
import io.koraframework.config.common.origin.ContainerConfigOrigin;
import io.koraframework.config.common.origin.FileConfigOrigin;
import io.koraframework.config.common.origin.SimpleConfigOrigin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;

import static io.koraframework.config.common.ConfigTestUtils.*;
import static org.assertj.core.api.Assertions.assertThat;


class ConfigWatcherTest {
    private final Path configDir = createConfigDir();
    private Path currentConfigDir = createCurrentDataDir(this.configDir, """
        database.username=test_user
        database.password=test_password
        """);
    private Path dataDir = createOrUpdateDataDir(this.configDir, this.currentConfigDir);
    private Path configFile = createConfigFile(this.configDir, this.dataDir);


    private ValueOf<Config> config;
    private InitializedGraph graph;

    ConfigWatcherTest() throws IOException {
    }


    @BeforeEach
    void setUp() throws InterruptedException {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(ConfigWatcher.class).setLevel(Level.TRACE);
        System.setProperty("config.file", configFile.toString());

        var draw = new ApplicationGraphDraw(ConfigWatcherTest.class);
        var originNode = draw.addNode(
            ConfigOrigin.class,
            null,
            null,
            List.of(),
            List.of(),
            List.of(),
            _ -> new ContainerConfigOrigin(
                new FileConfigOrigin(ConfigWatcherTest.this.configFile),
                new SimpleConfigOrigin("test")
            )
        );
        var configNode = draw.addNode(
            Config.class,
            null,
            null,
            List.of(ApplicationGraphDraw.singleDependency(originNode)),
            List.of(originNode),
            List.of(),
            g -> load(g.get(originNode))
        );
        draw.addNode(
            ConfigWatcher.class,
            null,
            null,
            List.of(ApplicationGraphDraw.singleDependency(originNode)),
            List.of(originNode),
            List.of(),
            g -> new ConfigWatcher(g, originNode, 50)
        );

        this.graph = draw.init();
        this.config = graph.valueOf(configNode);
        Thread.sleep(100);
    }

    @AfterEach
    void tearDown() throws Exception {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(ConfigWatcher.class).setLevel(null);
        this.graph.release();
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

    private Config load(ContainerConfigOrigin origin) throws IOException {
        var properties = new Properties();
        try (var is = Files.newInputStream(ConfigWatcherTest.this.configFile)) {
            properties.load(is);
        }
        return MapConfigFactory.fromProperties(origin, properties);
    }
}
