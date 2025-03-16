package ru.tinkoff.kora.cache.telemetry;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;

public class Sl4fjCacheLogger implements CacheLogger {

    private final Logger startLogger;
    private final Logger finishLogger;

    public Sl4fjCacheLogger(Logger requestLogger, Logger finishLogger) {
        this.startLogger = requestLogger;
        this.finishLogger = finishLogger;
    }

    @Override
    public void logStart(@Nonnull CacheTelemetryOperation operation) {
        var marker = StructuredArgument.marker("cacheOperation", gen -> {
            gen.writeStartObject();
            gen.writeStringField("name", operation.name());
            gen.writeStringField("cache", operation.cacheName());
            gen.writeStringField("origin", operation.origin());
            gen.writeEndObject();
        });

        startLogger.debug(marker, "Operation '{}' for cache '{}' started",
            operation.name(), operation.cacheName());
    }

    @Override
    public void logSuccess(@Nonnull CacheTelemetryOperation operation, long durationInNanos, @Nullable Object valueFromCache) {
        var marker = StructuredArgument.marker("cacheOperation", gen -> {
            gen.writeStartObject();
            gen.writeStringField("name", operation.name());
            gen.writeStringField("cache", operation.cacheName());
            gen.writeStringField("origin", operation.origin());
            gen.writeNumberField("processingTime", durationInNanos / 1_000_000);
            gen.writeEndObject();
        });

        if (operation.name().startsWith("GET")) {
            if (valueFromCache == null) {
                finishLogger.debug(marker, "Operation '{}' for cache '{}' didn't retried value",
                    operation.name(), operation.cacheName());
            } else {
                finishLogger.debug(marker, "Operation '{}' for cache '{}' retried value",
                    operation.name(), operation.cacheName());
            }
        } else {
            finishLogger.debug(marker, "Operation '{}' for cache '{}' completed",
                operation.name(), operation.cacheName());
        }
    }

    @Override
    public void logFailure(@Nonnull CacheTelemetryOperation operation, long durationInNanos, @Nullable Throwable exception) {
        var marker = StructuredArgument.marker("cacheOperation", gen -> {
            gen.writeStartObject();
            gen.writeStringField("name", operation.name());
            gen.writeStringField("cache", operation.cacheName());
            gen.writeStringField("origin", operation.origin());
            gen.writeNumberField("processingTime", durationInNanos / 1_000_000);
            if (exception != null) {
                var exceptionType = exception.getClass().getCanonicalName();
                gen.writeStringField("exceptionType", exceptionType);
            }
            gen.writeEndObject();
        });

        if (exception != null) {
            finishLogger.warn(marker, "Operation '{}' failed for cache '{}' with message: {}",
                operation.name(), operation.cacheName(), exception.getMessage());
        } else {
            finishLogger.warn(marker, "Operation '{}' failed for cache '{}'",
                operation.name(), operation.cacheName());
        }
    }
}
