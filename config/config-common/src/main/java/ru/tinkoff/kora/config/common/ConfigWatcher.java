package ru.tinkoff.kora.config.common;

import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.ValueOf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public class ConfigWatcher implements Lifecycle {
    private static final Logger log = LoggerFactory.getLogger(ConfigWatcher.class);

    private final ValueOf<Config> applicationConfig;
    private final AtomicBoolean isStarted = new AtomicBoolean(false);
    private final int checkTime;
    private volatile Thread thread;

    public ConfigWatcher(ValueOf<Config> applicationConfig, int checkTime) {
        this.applicationConfig = applicationConfig;
        this.checkTime = checkTime;
    }

    @Override
    public Mono<Void> init() {
        return Mono.fromRunnable(() -> {
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
            if (this.isStarted.compareAndSet(true, false)) {
                this.thread.interrupt();
                this.thread = null;
            }
        });
    }

    private void watchJob() {
        var filename = System.getProperty("config.file");
        if (filename == null) {
            log.debug("Empty config origin, watch job is cancelled");
            return;
        } else {
            log.info("Watching for config updates on {}", filename);
        }
        Path configPath;
        Instant lastModifiedTime;
        try {
            configPath = Paths.get(filename).toAbsolutePath().toRealPath();
            lastModifiedTime = Files.getLastModifiedTime(configPath).toInstant();
        } catch (IOException e) {
            log.warn("Can't locate config file or ", e);
            return;
        }
        while (this.isStarted.get()) {
            try {
                log.trace("Checking config path '{}' (last modified {}) for updates", filename, lastModifiedTime);
                var currentConfigPath = Paths.get(filename).toAbsolutePath().toRealPath();
                var currentLastModifiedTime = Files.getLastModifiedTime(currentConfigPath).toInstant();
                log.trace("Current config path '{}' (last modified {})", currentConfigPath, currentLastModifiedTime);
                if (!currentConfigPath.equals(configPath)) {
                    log.debug("New config symlink target");
                    configPath = currentConfigPath;
                    lastModifiedTime = currentLastModifiedTime;

                    this.applicationConfig.refresh().block();
                    log.info("Config refreshed");
                } else if (currentLastModifiedTime.isAfter(lastModifiedTime)) {
                    log.debug("Config modified");
                    configPath = currentConfigPath;
                    lastModifiedTime = currentLastModifiedTime;

                    this.applicationConfig.refresh().block();
                    log.info("Config refreshed");
                }
                configPath = currentConfigPath;
                lastModifiedTime = currentLastModifiedTime;
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
}
