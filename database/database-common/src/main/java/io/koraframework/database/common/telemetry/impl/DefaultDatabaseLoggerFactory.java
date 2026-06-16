package io.koraframework.database.common.telemetry.impl;

import io.koraframework.database.common.QueryContext;
import io.koraframework.logging.common.arg.StructuredArgument;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultDatabaseLoggerFactory {

    public static final DefaultDatabaseLoggerFactory INSTANCE = new DefaultDatabaseLoggerFactory();

    public DefaultDatabaseLogger create(DefaultDatabaseTelemetry.TelemetryContext context) {
        var logger = LoggerFactory.getLogger("io.koraframework.database." + context.poolName() + ".query");
        return new DefaultDatabaseLogger(logger, context);
    }

    public static class DefaultDatabaseLogger {

        protected final Logger logger;
        protected final DefaultDatabaseTelemetry.TelemetryContext context;

        public DefaultDatabaseLogger(Logger logger, DefaultDatabaseTelemetry.TelemetryContext context) {
            this.logger = logger;
            this.context = context;
        }

        public void logQueryBegin(QueryContext query) {
            if (!this.logger.isDebugEnabled()) {
                return;
            }
            this.logger.atDebug()
                .addKeyValue("sqlQuery", StructuredArgument.value(gen -> {
                    gen.writeStartObject();
                    gen.writeStringProperty("pool", this.context.poolName());
                    gen.writeStringProperty("operation", query.operation());
                    gen.writeStringProperty("queryId", query.queryId());
                    if (this.logger.isTraceEnabled()) {
                        gen.writeStringProperty("sql", query.sql());
                    }
                    gen.writeEndObject();
                }))
                .log("Executing query");
        }

        public void logQueryEnd(QueryContext query, @Nullable Throwable error, long processingTimeNanos) {
            if (error == null && !this.logger.isDebugEnabled()) {
                return;
            }
            if (error != null && !this.logger.isWarnEnabled()) {
                return;
            }
            var errorType = error == null ? null : error.getClass().getCanonicalName();
            var arg = StructuredArgument.value(gen -> {
                gen.writeStartObject();
                gen.writeStringProperty("pool", this.context.poolName());
                gen.writeStringProperty("operation", query.operation());
                gen.writeStringProperty("queryId", query.queryId());
                gen.writeNumberProperty("processingTime", processingTimeNanos / 1_000_000);
                if (errorType != null) {
                    gen.writeStringProperty("exceptionType", errorType);
                }
                if (this.logger.isTraceEnabled()) {
                    gen.writeStringProperty("sql", query.sql());
                }
                gen.writeEndObject();
            });
            if (error == null) {
                this.logger.atDebug()
                    .addKeyValue("sqlQuery", arg)
                    .log("Query executed");
            } else {
                this.logger.atWarn()
                    .addKeyValue("sqlQuery", arg)
                    .log("Query failed");
            }
        }
    }
}
