package io.koraframework.config.common;

import io.koraframework.application.graph.Lifecycle;
import io.koraframework.application.graph.Node;
import io.koraframework.application.graph.RefreshableGraph;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.config.common.origin.ConfigOrigin;
import io.koraframework.config.common.origin.ContainerConfigOrigin;
import io.koraframework.config.common.origin.FileConfigOrigin;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.zip.CRC32;

public class ConfigWatcher implements Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(ConfigWatcher.class);

    private final AtomicBoolean isStarted = new AtomicBoolean(false);

    private final RefreshableGraph graph;
    private final @Nullable Node<? extends ConfigOrigin> applicationConfigNode;
    private final @Nullable ValueOf<ConfigOrigin> applicationConfig;
    private final int checkTime;

    private volatile Thread thread;

    public ConfigWatcher(RefreshableGraph graph,
                         @Nullable Node<? extends ConfigOrigin> applicationConfigNode,
                         @Nullable ValueOf<ConfigOrigin> applicationConfig,
                         Duration checkTime) {
        this.graph = graph;
        this.applicationConfigNode = applicationConfigNode;
        this.applicationConfig = applicationConfig;
        this.checkTime = ((int) checkTime.toMillis());
    }

    @Override
    public void init() {
        if (this.applicationConfigNode == null) {
            return;
        }

        var enableConfigWatch = System.getenv("KORA_CONFIG_WATCHER_ENABLED");
        if (enableConfigWatch == null) {
            enableConfigWatch = System.getProperty("kora.config.watcher.enabled");
        }

        if (enableConfigWatch != null && !Boolean.parseBoolean(enableConfigWatch)) {
            return;
        } else if (this.isStarted.compareAndSet(false, true)) {
            this.thread = Thread.ofVirtual()
                .name("config-reload")
                .start(this::watchJob);
        }
    }

    @Override
    public void release() {
        if (this.applicationConfigNode == null) {
            return;
        }
        if (this.isStarted.compareAndSet(true, false)) {
            this.thread.interrupt();
            this.thread = null;
        }
    }

    private void watchJob() {
        if (this.applicationConfigNode == null || this.applicationConfig == null) {
            return;
        }
        // the value comes in as a dependency, so the graph has already initialized the config node
        // by the time this thread starts and reading it here cannot race with initialization
        ConfigOrigin config = this.applicationConfig.get();
        var origins = this.parseOrigin(config);
        // any difference counts as a change: an mtime can go back (a restored backup) or stay the same
        // (two saves within one timestamp tick), so the content is compared as well
        // the whole file is read on every check; config files are small
        record State(Path configPath, Instant lastModifiedTime, long contentChecksum) {}
        Function<Path, State> stateExtractor = configuredPath -> {
            try {
                var configPath = configuredPath.toAbsolutePath().toRealPath();
                var lastModifiedTime = Files.getLastModifiedTime(configPath).toInstant();
                var checksum = new CRC32();
                checksum.update(Files.readAllBytes(configPath));
                return new State(configPath, lastModifiedTime, checksum.getValue());
            } catch (IOException e) {
                logger.warn("Can't locate config file or ", e);
                return null;
            }
        };
        var state = new HashMap<Path, State>();
        for (var origin : origins) {
            var originalState = stateExtractor.apply(origin.path());
            state.put(origin.path(), originalState);
        }
        while (this.isStarted.get()) {
            var newConfig = this.graph.get(this.applicationConfigNode);
            if (config != newConfig) {
                config = newConfig;
                var changed = false;
                origins = this.parseOrigin(config);

                // a refresh recreates the origin object even when its files are the same, so already tracked
                // files keep their state: dropping it made every refresh look like a changed origin and refresh again
                var newStates = new HashMap<Path, State>();
                for (var origin : origins) {
                    var path = origin.path();
                    if (state.containsKey(path)) {
                        newStates.put(path, state.get(path));
                    } else {
                        logger.debug("New config origin {}", origin);
                        changed = true;
                        newStates.put(path, stateExtractor.apply(path));
                    }
                }
                for (var oldPath : state.keySet()) {
                    if (!newStates.containsKey(oldPath)) {
                        logger.debug("Config origin {} no more present in graph", oldPath);
                        changed = true;
                    }
                }
                state = newStates;
                if (changed && this.isStarted.get()) {
                    try {
                        this.graph.refresh(this.applicationConfigNode);
                        logger.info("Config refreshed");
                        Thread.sleep(this.checkTime);
                    } catch (InterruptedException ignore) {
                    } catch (Throwable e) {
                        logger.warn("Error on checking config for changes", e);
                        try {
                            Thread.sleep(this.checkTime);
                        } catch (InterruptedException ignore) {
                        }
                    }
                }
                continue;
            }

            var changed = new HashMap<Path, State>();
            for (var entry : state.entrySet()) {
                var path = entry.getKey();
                var newState = stateExtractor.apply(path);
                if (newState == null) {
                    continue;
                }
                if (!newState.equals(entry.getValue())) {
                    logger.debug("Config file {} changed", path);
                    changed.put(path, newState);
                }
            }
            try {
                // the graph may be released while the files were being checked
                if (!changed.isEmpty() && this.isStarted.get()) {
                    this.graph.refresh(this.applicationConfigNode);
                    logger.info("Config refreshed");
                    state.putAll(changed);
                }
                Thread.sleep(this.checkTime);
            } catch (InterruptedException ignore) {
            } catch (Throwable e) {
                // an Error (e.g. NoClassDefFoundError of a component the new config switches on) must not stop the watcher
                logger.warn("Error on checking config for changes", e);
                try {
                    Thread.sleep(this.checkTime);
                } catch (InterruptedException ignore) {
                }
            }
        }
    }


    private List<FileConfigOrigin> parseOrigin(ConfigOrigin origin) {
        if (origin instanceof FileConfigOrigin o) {
            return List.of(o);
        }
        if (origin instanceof ContainerConfigOrigin o) {
            var result = new ArrayList<FileConfigOrigin>();
            for (var configOrigin : o.origins()) {
                result.addAll(parseOrigin(configOrigin));
            }
            return result;
        }
        return List.of();
    }
}
