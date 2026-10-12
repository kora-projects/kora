package io.koraframework.config.common;

import io.koraframework.application.graph.ApplicationGraphDraw;
import io.koraframework.application.graph.InitializedGraph;
import io.koraframework.application.graph.Node;
import io.koraframework.application.graph.NodeWithMapper;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.config.common.origin.ConfigOrigin;
import io.koraframework.config.common.origin.ContainerConfigOrigin;
import io.koraframework.config.common.origin.FileConfigOrigin;
import io.koraframework.config.common.util.ConfigMappingUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uses plain files only, so it also runs where {@link ConfigWatcherTest} cannot create symbolic links.
 */
class ConfigWatcherRefreshTest {

    private static final Duration CHECK_TIME = Duration.ofMillis(50);
    // 20 watcher checks without any file change
    private static final Duration STABLE_PERIOD = CHECK_TIME.multipliedBy(20);

    @TempDir
    Path dir;

    private final AtomicInteger configLoads = new AtomicInteger();
    private InitializedGraph graph;
    private ValueOf<Config> config;

    @AfterEach
    void tearDown() throws Exception {
        if (this.graph != null) {
            this.graph.release();
        }
    }

    @Test
    void fileOriginRefreshesOncePerChange() throws Exception {
        var main = write("main.properties", "value=1");
        init(() -> new FileConfigOrigin(main));

        change(main, "value=2");
        awaitValue("2");
        assertLoadsStayAt(2);

        change(main, "value=3");
        awaitValue("3");
        assertLoadsStayAt(3);
    }

    @Test
    void containerOriginRefreshesOncePerChange() throws Exception {
        var main = write("main.properties", "value=1");
        var included = write("included.properties", "other=1");
        init(() -> new ContainerConfigOrigin(List.of(new FileConfigOrigin(main), new FileConfigOrigin(included))));

        change(main, "value=2");
        awaitValue("2");
        assertLoadsStayAt(2);

        // a change of an included file is detected as well
        change(included, "other=2");
        awaitLoads(3);
        assertLoadsStayAt(3);
    }

    @Test
    void addedOriginIsWatchedAfterRefresh() throws Exception {
        var main = write("main.properties", "value=1");
        var included = write("included.properties", "other=1");
        var origins = new AtomicReference<List<ConfigOrigin>>(List.of(new FileConfigOrigin(main)));
        init(() -> new ContainerConfigOrigin(origins.get()));

        origins.set(List.of(new FileConfigOrigin(main), new FileConfigOrigin(included)));
        change(main, "value=2");
        awaitValue("2");
        // the new origin may cause one more refresh, but the watcher must settle
        var settled = awaitStableLoads();

        change(included, "other=2");
        awaitLoads(settled + 1);
        assertLoadsStayAt(settled + 1);
    }

    @Test
    void removedOriginIsNotWatchedAfterRefresh() throws Exception {
        var main = write("main.properties", "value=1");
        var included = write("included.properties", "other=1");
        var origins = new AtomicReference<List<ConfigOrigin>>(List.of(new FileConfigOrigin(main), new FileConfigOrigin(included)));
        init(() -> new ContainerConfigOrigin(origins.get()));

        origins.set(List.of(new FileConfigOrigin(main)));
        change(main, "value=2");
        awaitValue("2");
        var settled = awaitStableLoads();

        change(included, "other=2");
        assertLoadsStayAt(settled);
    }

    @Test
    void unchangedSectionKeepsDependents() throws Exception {
        var main = write("main.properties", "value=1\napp.a=1");
        var dependentCreations = new AtomicInteger();
        init(() -> new FileConfigOrigin(main), (draw, configNode) -> {
            var sectionNode = draw.addNode(ConfigValue.ObjectValue.class, null, null, List.of(configNode), List.of(configNode), List.of(),
                g -> g.get(configNode).get("app").asObject());
            draw.addNode(Object.class, null, null, List.of(sectionNode), List.of(sectionNode), List.of(), g -> {
                dependentCreations.incrementAndGet();
                return g.get(sectionNode);
            });
        });
        assertThat(dependentCreations.get()).isEqualTo(1);

        change(main, "value=2\napp.a=1");
        awaitValue("2");
        assertThat(dependentCreations.get()).as("dependent of an unchanged config section").isEqualTo(1);

        change(main, "value=2\napp.a=2");
        // the config node is rebuilt in the middle of the refresh, the dependent after it, so wait for the dependent
        var deadline = Instant.now().plusSeconds(10);
        while (dependentCreations.get() < 2 && Instant.now().isBefore(deadline)) {
            Thread.sleep(10);
        }
        assertThat(dependentCreations.get()).as("dependent of a changed config section").isEqualTo(2);
        assertThat(this.configLoads.get()).isEqualTo(3);
    }

    private void init(Supplier<ConfigOrigin> originFactory) throws InterruptedException {
        init(originFactory, (_, _) -> {});
    }

    private void init(Supplier<ConfigOrigin> originFactory, BiConsumer<ApplicationGraphDraw, Node<Config>> extraNodes) throws InterruptedException {
        var draw = new ApplicationGraphDraw(ConfigWatcherRefreshTest.class);
        var originNode = draw.addNode(ConfigOrigin.class, null, null, List.of(), List.of(), List.of(), _ -> originFactory.get());
        var configNode = draw.addNode(Config.class, null, null, List.of(originNode), List.of(originNode), List.of(), g -> {
            this.configLoads.incrementAndGet();
            return load(g.get(originNode));
        });
        // wired like the generated graph for ConfigModule#applicationConfigWatcher: Node and ValueOf parameters are
        // not refresh dependencies, so the watcher survives config refreshes with its own file state
        draw.addNode(ConfigWatcher.class, null, null, List.of(originNode), List.of(), List.of(),
            g -> new ConfigWatcher(g, originNode, g.getOneValueOf(NodeWithMapper.node(originNode)), CHECK_TIME));
        extraNodes.accept(draw, configNode);

        this.graph = draw.init();
        this.config = this.graph.valueOf(configNode);
        assertLoadsStayAt(1);
    }

    private Path write(String name, String content) throws IOException {
        return Files.writeString(this.dir.resolve(name), content);
    }

    /**
     * Moves the modification time forward explicitly, file systems may keep it with a coarse granularity.
     * The new content and time are prepared aside and moved in atomically: writing the file and then setting its time
     * are two changes the watcher can observe separately, which made it refresh twice.
     */
    private static void change(Path file, String content) throws IOException {
        var lastModified = Files.getLastModifiedTime(file).toInstant();
        var next = Files.writeString(file.resolveSibling(file.getFileName() + ".next"), content);
        Files.setLastModifiedTime(next, FileTime.from(lastModified.plusSeconds(10)));
        Files.move(next, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private void awaitValue(String expected) throws InterruptedException {
        var deadline = Instant.now().plusSeconds(10);
        while (!expected.equals(this.config.get().get("value").asString()) && Instant.now().isBefore(deadline)) {
            Thread.sleep(10);
        }
        assertThat(this.config.get().get("value").asString()).isEqualTo(expected);
    }

    private void awaitLoads(int expected) throws InterruptedException {
        var deadline = Instant.now().plusSeconds(10);
        while (this.configLoads.get() < expected && Instant.now().isBefore(deadline)) {
            Thread.sleep(10);
        }
        assertThat(this.configLoads.get()).isEqualTo(expected);
    }

    private int awaitStableLoads() throws InterruptedException {
        var deadline = Instant.now().plusSeconds(10);
        var loads = this.configLoads.get();
        while (Instant.now().isBefore(deadline)) {
            Thread.sleep(STABLE_PERIOD.toMillis());
            var current = this.configLoads.get();
            if (current == loads) {
                return loads;
            }
            loads = current;
        }
        throw new AssertionError("Config keeps refreshing without file changes, loads: " + this.configLoads.get());
    }

    private void assertLoadsStayAt(int expected) throws InterruptedException {
        Thread.sleep(STABLE_PERIOD.toMillis());
        assertThat(this.configLoads.get())
            .as("config loads after %s without file changes", STABLE_PERIOD)
            .isEqualTo(expected);
    }

    private static Config load(ConfigOrigin origin) {
        var properties = new Properties();
        var files = origin instanceof ContainerConfigOrigin container ? container.origins() : List.of(origin);
        for (var file : files) {
            try (var is = Files.newInputStream(((FileConfigOrigin) file).path())) {
                properties.load(is);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return ConfigMappingUtils.fromProperties(origin, properties);
    }
}
