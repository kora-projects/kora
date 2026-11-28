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

    private static final class WatchJobRunnable implements Runnable {
        private final ConfigWatcher watcher;

        public WatchJobRunnable(ConfigWatcher watcher) {
            this.watcher = watcher;
        }

        @Override
        public void run() {
            this.watcher.watchJob();
        }
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
        }
        if (this.isStarted.compareAndSet(false, true)) {
            this.thread = Thread.ofVirtual()
                .name("config-reload")
                .start(new WatchJobRunnable(this));
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

    record State(Path configPath, Instant lastModifiedTime) {}

    private State extractState(Path configuredPath) {
        try {
            var configPath = configuredPath.toAbsolutePath().toRealPath();
            var lastModifiedTime = Files.getLastModifiedTime(configPath).toInstant();
            return new State(configPath, lastModifiedTime);
        } catch (IOException e) {
            logger.warn("Can't locate config file or ", e);
            return null;
        }
    }

    private void watchJob() {
        if (this.applicationConfigNode == null || this.applicationConfig == null) {
            return;
        }
        ConfigOrigin config = this.applicationConfig.get();
        var origins = this.parseOrigin(config);

        var state = new HashMap<Path, State>();
        for (var origin : origins) {
            var originalState = this.extractState(origin.path());
            state.put(origin.path(), originalState);
        }
        while (isStarted.get()) {
            try {
                Thread.sleep(checkTime);
            } catch (InterruptedException ignore) {
                break;
            }
            var newConfig = this.graph.get(this.applicationConfigNode);
            if (config != newConfig) {
                var changed = false;
                origins = this.parseOrigin(config);

                var newStates = new HashMap<Path, State>();
                for (var origin : origins) {
                    var path = origin.path();
                    if (state.containsKey(path)) {
                        newStates.put(path, state.get(path));
                    } else {
                        logger.debug("New config origin {}", origin);
                        changed = true;
                        newStates.put(path, this.extractState(path));
                    }
                }
                for (var oldPath : state.keySet()) {
                    if (!newStates.containsKey(oldPath)) {
                        logger.debug("Config origin {} no more present in graph", oldPath);
                        changed = true;
                    }
                }
                state = newStates;
                if (changed) {
                    try {
                        this.graph.refresh(this.applicationConfigNode);
                        logger.info("Config refreshed");
                    } catch (Exception e) {
                        logger.warn("Error on checking config for changes", e);
                    }
                }
                continue;
            }

            var changed = new HashMap<Path, State>();
            for (var entry : state.entrySet()) {
                var path = entry.getKey();
                var newState = this.extractState(path);
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
                var configPath1 = newState.configPath;
                var lastModifiedTime1 = newState.lastModifiedTime;
                if (!configPath1.equals(configPath)) {
                    logger.debug("New config symlink target");
                    changed.put(entry.getKey(), newState);
                } else if (lastModifiedTime1.isAfter(lastModifiedTime)) {
                    logger.debug("Config modified");
                    changed.put(entry.getKey(), newState);
                }
            }

            if (!changed.isEmpty()) {
                try {
                    this.graph.refresh(this.applicationConfigNode);
                    logger.info("Config refreshed");
                    state.putAll(changed);
                } catch (Exception e) {
                    logger.warn("Error on checking config for changes", e);
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
