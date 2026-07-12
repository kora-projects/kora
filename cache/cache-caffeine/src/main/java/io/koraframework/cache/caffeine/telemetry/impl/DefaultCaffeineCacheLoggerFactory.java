package io.koraframework.cache.caffeine.telemetry.impl;

import io.koraframework.cache.caffeine.telemetry.CaffeineCacheTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public class DefaultCaffeineCacheLoggerFactory {

    public static final DefaultCaffeineCacheLoggerFactory INSTANCE = new DefaultCaffeineCacheLoggerFactory();

    public DefaultCaffeineCacheLogger create(DefaultCaffeineCacheTelemetry.TelemetryContext context) {
        var logger = LoggerFactory.getLogger(context.cacheImplCanonicalName());
        return new DefaultCaffeineCacheLogger(logger, context);
    }

    public static class DefaultCaffeineCacheLogger {

        protected final Logger logger;
        protected final DefaultCaffeineCacheTelemetry.TelemetryContext context;

        public DefaultCaffeineCacheLogger(Logger logger, DefaultCaffeineCacheTelemetry.TelemetryContext context) {
            this.logger = logger;
            this.context = context;
        }

        public void logStart(CaffeineCacheTelemetry.Operation operation, Object key) {
            if (this.logger.isDebugEnabled()) {
                this.logger.atTrace()
                    .addKeyValue("operation", operation)
                    .addKeyValue("cacheConfigPath", this.context.cacheConfigPath())
                    .addKeyValue("cacheImpl", this.context.cacheImplCanonicalName())
                    .addKeyValue("key", key)
                    .log("Caffeine Cache operation started");
            }
        }

        public void logStart(CaffeineCacheTelemetry.Operation operation, Collection<?> keys) {
            if (this.logger.isTraceEnabled()) {
                this.logger.atTrace()
                    .addKeyValue("operation", operation)
                    .addKeyValue("cacheConfigPath", this.context.cacheConfigPath())
                    .addKeyValue("cacheImpl", this.context.cacheImplCanonicalName())
                    .addKeyValue("keys", keys.size())
                    .log("Caffeine Cache operation started");
            }
        }

        public void logEnd(CaffeineCacheTelemetry.Operation operation, Throwable error) {
            if (this.logger.isWarnEnabled()) {
                this.logger.atWarn()
                    .addKeyValue("operation", operation)
                    .addKeyValue("cacheConfigPath", this.context.cacheConfigPath())
                    .addKeyValue("cacheImpl", this.context.cacheImplCanonicalName())
                    .log("Caffeine Cache operation failed due to: {}", error.getMessage());
            }
        }

        public void logEnd(CaffeineCacheTelemetry.Operation operation, int retrieved, int missed) {
            if (this.logger.isTraceEnabled()) {
                var builder = retrieved > 0
                    ? this.logger.atDebug()
                    : this.logger.atTrace();

                builder
                    .addKeyValue("operation", operation)
                    .addKeyValue("cacheConfigPath", this.context.cacheConfigPath())
                    .addKeyValue("cacheImpl", this.context.cacheImplCanonicalName());

                if (operation.hasCacheResult()) {
                    builder.addKeyValue("retrieved", retrieved);
                    builder.addKeyValue("missed", missed);
                }

                builder.log("Caffeine Cache operation completed");
            }
        }
    }
}
