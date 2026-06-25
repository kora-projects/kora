package io.koraframework.cache.redis.telemetry.impl;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public class DefaultRedisCacheLoggerFactory {

    public static final DefaultRedisCacheLoggerFactory INSTANCE = new DefaultRedisCacheLoggerFactory();

    public DefaultRedisCacheLogger create(DefaultRedisCacheTelemetry.TelemetryContext context) {
        var logger = LoggerFactory.getLogger(context.cacheImplCanonicalName());
        return new DefaultRedisCacheLogger(logger, context);
    }

    public static class DefaultRedisCacheLogger {

        protected final Logger logger;
        protected final DefaultRedisCacheTelemetry.TelemetryContext context;

        public DefaultRedisCacheLogger(Logger logger, DefaultRedisCacheTelemetry.TelemetryContext context) {
            this.logger = logger;
            this.context = context;
        }

        public void logStart(String operation, Object key) {
            if (this.logger.isTraceEnabled()) {
                this.logger.atTrace()
                    .addKeyValue("operation", operation)
                    .addKeyValue("cacheConfigPath", this.context.cacheConfigPath())
                    .addKeyValue("cacheImpl", this.context.cacheImplCanonicalName())
                    .addKeyValue("key", key)
                    .log("Redis Cache operation started");
            }
        }

        public void logStart(String operation, Collection<?> keys) {
            if (this.logger.isTraceEnabled()) {
                this.logger.atTrace()
                    .addKeyValue("operation", operation)
                    .addKeyValue("cacheConfigPath", this.context.cacheConfigPath())
                    .addKeyValue("cacheImpl", this.context.cacheImplCanonicalName())
                    .addKeyValue("keys", keys.size())
                    .log("Redis Cache operation started");
            }
        }

        public void logEnd(String operation, long startedInNanos, @Nullable Throwable error) {
            if (error == null) {
                this.logEnd(operation, startedInNanos, 0, 0);
                return;
            }

            if (this.logger.isWarnEnabled()) {
                this.logger.atWarn()
                    .addKeyValue("operation", operation)
                    .addKeyValue("cacheConfigPath", this.context.cacheConfigPath())
                    .addKeyValue("cacheImpl", this.context.cacheImplCanonicalName())
                    .addKeyValue("processingTime", (System.nanoTime() - startedInNanos) / 1_000_000)
                    .log("Redis Cache operation failed due to: {}", error.getMessage());
            }
        }

        public void logEnd(String operation, long startedInNanos, int retrieved, int missed) {
            if (this.logger.isDebugEnabled()) {
                var builder = retrieved > 0
                    ? this.logger.atDebug()
                    : this.logger.atTrace();

                builder
                    .addKeyValue("operation", operation)
                    .addKeyValue("cacheConfigPath", this.context.cacheConfigPath())
                    .addKeyValue("cacheImpl", this.context.cacheImplCanonicalName())
                    .addKeyValue("processingTime", (System.nanoTime() - startedInNanos) / 1_000_000);

                if (operation.startsWith("GET")) {
                    builder.addKeyValue("retrieved", retrieved);
                    builder.addKeyValue("missed", missed);
                }

                builder.log("Redis Cache operation completed");
            }
        }
    }
}
