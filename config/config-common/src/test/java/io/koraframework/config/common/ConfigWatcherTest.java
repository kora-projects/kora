package io.koraframework.config.common;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import io.koraframework.application.graph.ApplicationGraphDraw;
import io.koraframework.application.graph.InitializedGraph;
import io.koraframework.application.graph.NodeWithMapper;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.config.common.origin.ConfigOrigin;
import io.koraframework.config.common.origin.ContainerConfigOrigin;
import io.koraframework.config.common.origin.FileConfigOrigin;
import io.koraframework.config.common.origin.SimpleConfigOrigin;
import io.koraframework.config.common.util.ConfigMappingUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static io.koraframework.config.common.ConfigTestUtils.createConfigFile;
import static io.koraframework.config.common.ConfigTestUtils.createCurrentDataDir;
import static io.koraframework.config.common.ConfigTestUtils.createOrUpdateDataDir;
import static org.assertj.core.api.Assertions.assertThat;


class ConfigWatcherTest {

    private static class TestContext implements AutoCloseable {
        final InitializedGraph graph;
        final ValueOf<Config> config;
        final Path configDir;

        Path currentConfigDir;
        Path dataDir;
        Path configFile;

        TestContext(InitializedGraph graph, ValueOf<Config> config, Path configDir, Path currentConfigDir, Path dataDir, Path configFile) {
            this.graph = graph;
            this.config = config;
            this.configDir = configDir;
            this.currentConfigDir = currentConfigDir;
            this.dataDir = dataDir;
            this.configFile = configFile;
        }

        @Override
        public void close() throws Exception {
            this.graph.release();
            if (Files.exists(configDir)) {
                try (var s = Files.walk(configDir)) {
                    s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
                }
            }
        }
    }

    private TestContext createContext(String initialConfigContent) throws Exception {
        Path uniqueConfigDir = Path.of("build/config-watcher-tests-" + UUID.randomUUID()).toAbsolutePath();
        Files.createDirectories(uniqueConfigDir);

        Path currentConfigDir = createCurrentDataDir(uniqueConfigDir, initialConfigContent);
        Path dataDir = createOrUpdateDataDir(uniqueConfigDir, currentConfigDir);
        Path configFile = createConfigFile(uniqueConfigDir, dataDir);

        var draw = new ApplicationGraphDraw(ConfigWatcherTest.class);
        var originNode = draw.addNode(
            ConfigOrigin.class,
            null,
            null,
            List.of(),
            List.of(),
            List.of(),
            _ -> new ContainerConfigOrigin(
                new FileConfigOrigin(configFile),
                new SimpleConfigOrigin("test")
            )
        );

        var configNode = draw.addNode(
            Config.class,
            null,
            null,
            List.of(originNode),
            List.of(originNode),
            List.of(),
            g -> {
                var properties = new Properties();
                try (var is = Files.newInputStream(configFile)) {
                    properties.load(is);
                }
                return ConfigMappingUtils.fromProperties(g.get(originNode), properties);
            }
        );

        draw.addNode(
            ConfigWatcher.class,
            null,
            null,
            List.of(originNode),
            List.of(originNode),
            List.of(),
            g -> new ConfigWatcher(g, originNode, g.getOneValueOf(NodeWithMapper.node(originNode)), Duration.ofMillis(50))
        );

        var graph = draw.init();
        var configValueOf = graph.valueOf(configNode);
        Thread.sleep(100);

        return new TestContext(graph, configValueOf, uniqueConfigDir, currentConfigDir, dataDir, configFile);
    }

    @BeforeEach
    void setUp() {
        var factory = LoggerFactory.getILoggerFactory();
        if (factory instanceof LoggerContext loggerContext) {
            loggerContext.getLogger(ConfigWatcher.class).setLevel(Level.TRACE);
        }
    }

    @AfterEach
    void tearDown() {
        var factory = LoggerFactory.getILoggerFactory();
        if (factory instanceof LoggerContext loggerContext) {
            loggerContext.getLogger(ConfigWatcher.class).setLevel(null);
        }
    }

    @Test
    void configRefreshesOnNewDataDir() throws Exception {
        // given
        try (var ctx = createContext("""
            database.username=test_user
            database.password=test_password
            """)) {

            var oldConfig = ctx.config.get();

            // when
            ctx.currentConfigDir = createCurrentDataDir(ctx.configDir, """
                database.username=test_user1
                database.password=test_password
                """);
            ctx.dataDir = createOrUpdateDataDir(ctx.configDir, ctx.currentConfigDir);

            // then
            assertWithTimeout(Duration.ofSeconds(10), () -> {
                assertThat(oldConfig).isNotSameAs(ctx.config.get());
                assertThat(ctx.config.get().get("database.username").asString()).isEqualTo("test_user1");
                assertThat(ctx.config.get().get("database.password").asString()).isEqualTo("test_password");
            });
        }
    }

    @Test
    void configRefreshesOnSymlinkChange() throws Exception {
        // given
        try (var ctx = createContext("""
            database.username=test_user
            database.password=test_password
            """)) {

            var oldConfig = ctx.config.get();

            // when
            ctx.currentConfigDir = createCurrentDataDir(ctx.configDir, """
                database.username=test_user1
                database.password=test_password
                """);
            ctx.configFile = createConfigFile(ctx.configDir, ctx.currentConfigDir);

            // then
            assertWithTimeout(Duration.ofSeconds(10), () -> {
                assertThat(oldConfig).isNotSameAs(ctx.config.get());
                assertThat(ctx.config.get().get("database.username").asString()).isEqualTo("test_user1");
                assertThat(ctx.config.get().get("database.password").asString()).isEqualTo("test_password");
            });
        }
    }

    @Test
    void configRefreshesOnFieldChange() throws Exception {
        // given
        try (var ctx = createContext("""
            database.username=test_user
            database.password=test_password
            """)) {

            var oldConfig = ctx.config.get();
            Thread.sleep(10);

            // when
            Path tempFile = Files.createTempFile(ctx.configDir, "config-", ".tmp");
            Files.writeString(tempFile, """
            database.username=test_user1
            database.password=test_password
            """);
            Files.move(tempFile, ctx.configFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);

            // then
            assertWithTimeout(Duration.ofSeconds(10), () -> {
                assertThat(oldConfig).isNotSameAs(ctx.config.get());
                assertThat(ctx.config.get().get("database.username").asString()).isEqualTo("test_user1");
                assertThat(ctx.config.get().get("database.password").asString()).isEqualTo("test_password");
            });
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
}
