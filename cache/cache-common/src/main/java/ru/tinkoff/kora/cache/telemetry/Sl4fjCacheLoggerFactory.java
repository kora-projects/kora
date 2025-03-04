package ru.tinkoff.kora.cache.telemetry;

import jakarta.annotation.Nullable;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.cache.Cache;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public final class Sl4fjCacheLoggerFactory implements CacheLoggerFactory {

    @Nullable
    @Override
    public CacheLogger get(TelemetryConfig.LogConfig logging, CacheTelemetryArgs args) {
        if (Objects.requireNonNullElse(logging.enabled(), false)) {
            var startLogger = LoggerFactory.getLogger(Cache.class.getPackageName() + ".start." + args.origin() + "." + args.cacheName());
            var finishLogger = LoggerFactory.getLogger(Cache.class.getPackageName() + ".finish." + args.origin() + "." + args.cacheName());
            return new Sl4fjCacheLogger(startLogger, finishLogger);
        } else {
            return null;
        }
    }
}
