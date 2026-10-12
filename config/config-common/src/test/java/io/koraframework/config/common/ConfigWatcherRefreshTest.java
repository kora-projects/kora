package io.koraframework.config.common;

import io.koraframework.application.graph.ApplicationGraphDraw;
import io.koraframework.application.graph.InitializedGraph;
import io.koraframework.application.graph.Lifecycle;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
    void fileReplacedWithOlderModificationTimeIsReloaded() throws Exception {
        var main = write("main.properties", "value=1");
        init(() -> new FileConfigOrigin(main));

        // e.g. a backup restored with `mv` or `cp -p`: the content changes, the modification time goes back
        var restored = write("restored.properties", "value=2");
        Files.setLastModifiedTime(restored, FileTime.from(Files.getLastModifiedTime(main).toInstant().minusSeconds(60)));
        Files.move(restored, main, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        awaitValue("2");
    }

    @Test
    void fileRewrittenWithSameModificationTimeIsReloaded() throws Exception {
        var main = write("main.properties", "value=1");
        init(() -> new FileConfigOrigin(main));

        var time = FileTime.from(Files.getLastModifiedTime(main).toInstant().plusSeconds(10));
        Files.writeString(main, "value=2");
        Files.setLastModifiedTime(main, time);
        awaitValue("2");
        // a second save within one timestamp tick of a coarse file system keeps the modification time
        Files.writeString(main, "value=3");
        Files.setLastModifiedTime(main, time);
        awaitValue("3");
    }

    @Test
    void watcherSurvivesErrorDuringRefresh() throws Exception {
        record Service(String value) {}
        var main = write("main.properties", "value=1");
        var draw = new ApplicationGraphDraw(ConfigWatcherRefreshTest.class);
        var originNode = draw.addNode(ConfigOrigin.class, null, null, List.of(), List.of(), List.of(), _ -> new FileConfigOrigin(main));
        var configNode = draw.addNode(Config.class, null, null, List.of(originNode), List.of(originNode), List.of(), g -> load(g.get(originNode)));
        var serviceNode = draw.addNode(Service.class, null, null, List.of(configNode), List.of(configNode), List.of(), g -> {
            var value = g.get(configNode).get("value").asString();
            if (value.equals("broken")) {
                // e.g. a component the new config switches on misses an optional dependency
                throw new NoClassDefFoundError("com/example/OptionalDependency");
            }
            return new Service(value);
        });
        draw.addNode(ConfigWatcher.class, null, null, List.of(originNode), List.of(), List.of(),
            g -> new ConfigWatcher(g, originNode, g.getOneValueOf(NodeWithMapper.node(originNode)), CHECK_TIME));
        this.graph = draw.init();
        this.config = this.graph.valueOf(configNode);

        change(main, "value=broken");
        Thread.sleep(STABLE_PERIOD.toMillis());
        assertThat(this.graph.get(serviceNode).value()).isEqualTo("1");

        change(main, "value=2");
        awaitValue("2");
        assertThat(this.graph.get(serviceNode).value()).isEqualTo("2");
    }

    @Test
    void nothingIsCreatedAfterGraphRelease() throws Exception {
        // many tracked files make one watcher check long, so the graph is released in the middle of it
        var files = new ArrayList<ConfigOrigin>();
        for (int i = 0; i < 20000; i++) {
            files.add(new FileConfigOrigin(write("f" + i + ".properties", "x")));
        }
        var last = ((FileConfigOrigin) files.getLast()).path();
        var initialized = new AtomicInteger();
        var released = new AtomicInteger();

        for (int attempt = 0; attempt < 10; attempt++) {
            initialized.set(0);
            released.set(0);
            var draw = new ApplicationGraphDraw(ConfigWatcherRefreshTest.class);
            var originNode = draw.addNode(ConfigOrigin.class, null, null, List.of(), List.of(), List.of(), _ -> new ContainerConfigOrigin(files));
            draw.addNode(Lifecycle.class, null, null, List.of(originNode), List.of(originNode), List.of(), _ -> new Lifecycle() {
                @Override
                public void init() {initialized.incrementAndGet();}

                @Override
                public void release() {released.incrementAndGet();}
            });
            draw.addNode(ConfigWatcher.class, null, null, List.of(originNode), List.of(), List.of(),
                g -> new ConfigWatcher(g, originNode, g.getOneValueOf(NodeWithMapper.node(originNode)), Duration.ofMillis(1)));
            var graph = draw.init();
            Thread.sleep(500); // the watcher took the initial file state

            Files.setLastModifiedTime(last, FileTime.from(Files.getLastModifiedTime(last).toInstant().plusSeconds(10)));
            graph.release();
            Thread.sleep(1500);
            assertThat(initialized.get() - released.get())
                .as("components created after graph.release() and never released, attempt %d", attempt)
                .isZero();
        }
    }

    private void init(Supplier<ConfigOrigin> originFactory) throws InterruptedException {
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
