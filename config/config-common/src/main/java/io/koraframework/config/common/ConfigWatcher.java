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
        record State(Path configPath, Instant lastModifiedTime) {}
        Function<Path, State> stateExtractor = configuredPath -> {
            try {
                var configPath = configuredPath.toAbsolutePath().toRealPath();
                var lastModifiedTime = Files.getLastModifiedTime(configPath).toInstant();
                return new State(configPath, lastModifiedTime);
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

                var newStates = new HashMap<Path, State>();
                for (var origin : origins) {
                    var currentState = state.get(origin.path());
                    if (currentState == null) {
                        logger.debug("New config origin {} no more present in graph", origin);
                        changed = true;
                        var originalState = stateExtractor.apply(origin.path());
                        newStates.put(origin.path(), originalState);
                    }
                }
                for (var entry : state.entrySet()) {
                    var oldPath = entry.getKey();
                    var oldState = state.get(oldPath);
                    var newState = newStates.get(oldPath);
                    if (newState == null) {
                        changed = true;
                        logger.debug("Config origin {} no more present in graph", entry.getKey());
                    } else if (!newState.configPath.equals(oldState.configPath)) {
                        logger.debug("New config symlink target");
                        changed = true;
                    } else if (newState.lastModifiedTime.isAfter(oldState.lastModifiedTime)) {
                        logger.debug("Config modified");
                        changed = true;
                    }
                }
                state = newStates;
                if (changed) {
                    try {
                        this.graph.refresh(this.applicationConfigNode);
                        logger.info("Config refreshed");
                        Thread.sleep(this.checkTime);
                    } catch (InterruptedException ignore) {
                    } catch (Exception e) {
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
                if (entry.getValue() == null) {
                    logger.debug("New config symlink target");
                    changed.put(entry.getKey(), newState);
                    continue;
                }
                var configPath = entry.getValue().configPath();
                var lastModifiedTime = entry.getValue().lastModifiedTime();
                var currentConfigPath = newState.configPath;
                var currentLastModifiedTime = newState.lastModifiedTime;
                if (!currentConfigPath.equals(configPath)) {
                    logger.debug("New config symlink target");
                    changed.put(entry.getKey(), newState);
                } else if (currentLastModifiedTime.isAfter(lastModifiedTime)) {
                    logger.debug("Config modified");
                    changed.put(entry.getKey(), newState);
                }
            }
            try {
                if (!changed.isEmpty()) {
                    this.graph.refresh(this.applicationConfigNode);
                    logger.info("Config refreshed");
                    state.putAll(changed);
                }
                Thread.sleep(this.checkTime);
            } catch (InterruptedException ignore) {
            } catch (Exception e) {
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
