package ru.tinkoff.kora.config.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.config.common.origin.ConfigOrigin;
import ru.tinkoff.kora.config.common.origin.ContainerConfigOrigin;
import ru.tinkoff.kora.config.common.origin.FileConfigOrigin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public class ConfigWatcher implements Lifecycle {
    private static final Logger log = LoggerFactory.getLogger(ConfigWatcher.class);

    private final Optional<ValueOf<Config>> applicationConfig;
    private final AtomicBoolean isStarted = new AtomicBoolean(false);
    private final int checkTime;
    private volatile Thread thread;

    public ConfigWatcher(Optional<ValueOf<Config>> applicationConfig, int checkTime) {
        this.applicationConfig = applicationConfig;
        this.checkTime = checkTime;
    }

    @Override
    public Mono<Void> init() {
        return Mono.fromRunnable(() -> {
            if (this.applicationConfig.isEmpty()) {
                return;
            }
            if (this.isStarted.compareAndSet(false, true)) {
                this.thread = new Thread(this::watchJob);
                this.thread.setName("config-reload");
                this.thread.start();
            }
        });
    }

    @Override
    public Mono<Void> release() {
        return Mono.fromRunnable(() -> {
            if (this.applicationConfig.isEmpty()) {
                return;
            }
            if (this.isStarted.compareAndSet(true, false)) {
                this.thread.interrupt();
                this.thread = null;
            }
        });
    }

    private void watchJob() {
        if (this.applicationConfig.isEmpty()) {
            return;
        }
        var config = this.applicationConfig.get().get();
        var origins = this.parseOrigin(config.origin());
        if (origins.isEmpty()) {
            return;
        }
        record State(Path configPath, Instant lastModifiedTime) {}
        Function<Path, State> stateExtractor = configuredPath -> {
            try {
                var configPath = configuredPath.toAbsolutePath().toRealPath();
                var lastModifiedTime = Files.getLastModifiedTime(configPath).toInstant();
                return new State(configPath, lastModifiedTime);
            } catch (IOException e) {
                log.warn("Can't locate config file or ", e);
                return null;
            }
        };
        var state = new HashMap<Path, State>();
        for (var origin : origins) {
            var originalState = stateExtractor.apply(origin.path());
            state.put(origin.path(), originalState);
        }
        while (this.isStarted.get()) {
            var changed = new HashMap<Path, State>();
            for (var entry : state.entrySet()) {
                var path = entry.getKey();
                var newState = stateExtractor.apply(path);
                if (newState == null) {
                    continue;
                }
                if (entry.getValue() == null) {
                    log.debug("New config symlink target");
                    changed.put(entry.getKey(), newState);
                    continue;
                }
                var configPath = entry.getValue().configPath();
                var lastModifiedTime = entry.getValue().lastModifiedTime();
                var currentConfigPath = newState.configPath;
                var currentLastModifiedTime = newState.lastModifiedTime;
                if (!currentConfigPath.equals(configPath)) {
                    log.debug("New config symlink target");
                    changed.put(entry.getKey(), newState);
                } else if (currentLastModifiedTime.isAfter(lastModifiedTime)) {
                    log.debug("Config modified");
                    changed.put(entry.getKey(), newState);
                }
            }
            try {
                if (!changed.isEmpty()) {
                    this.applicationConfig.get().refresh().block();
                    log.info("Config refreshed");
                    state.putAll(changed);
                }
                Thread.sleep(this.checkTime);
            } catch (InterruptedException ignore) {
            } catch (Exception e) {
                log.warn("Error on checking config for changes", e);
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
