package io.koraframework.database.jdbc.telemetry.impl;

import io.koraframework.database.jdbc.telemetry.JdbcTransactionContext;
import io.koraframework.logging.common.arg.StructuredArgument;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultJdbcDatabaseLoggerFactory {

    public static final DefaultJdbcDatabaseLoggerFactory INSTANCE = new DefaultJdbcDatabaseLoggerFactory();

    public DefaultJdbcDatabaseLogger create(DefaultJdbcDatabaseTelemetry.TelemetryContext context) {
        var logger = LoggerFactory.getLogger("io.koraframework.database." + context.poolName() + ".transaction");
        return new DefaultJdbcDatabaseLogger(logger, context);
    }

    public static class DefaultJdbcDatabaseLogger {

        protected final Logger logger;
        protected final DefaultJdbcDatabaseTelemetry.TelemetryContext context;

        public DefaultJdbcDatabaseLogger(Logger logger, DefaultJdbcDatabaseTelemetry.TelemetryContext context) {
            this.logger = logger;
            this.context = context;
        }

        public void logTransactionBegin(JdbcTransactionContext transaction) {
            if (!this.logger.isDebugEnabled()) {
                return;
            }
            this.logger.atDebug()
                .addKeyValue("sqlTransaction", StructuredArgument.value(gen -> {
                    gen.writeStartObject();
                    gen.writeStringProperty("pool", this.context.poolName());
                    gen.writeStringProperty("isolationLevel", transaction.isolationLevel());
                    gen.writeEndObject();
                }))
                .log("Transaction started");
        }

        public void logTransactionEnd(JdbcTransactionContext transaction, boolean committed, @Nullable Throwable error, long processingTimeNanos) {
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
                gen.writeStringProperty("isolationLevel", transaction.isolationLevel());
                gen.writeStringProperty("outcome", committed ? "commit" : "rollback");
                gen.writeNumberProperty("processingTime", processingTimeNanos / 1_000_000);
                if (errorType != null) {
                    gen.writeStringProperty("exceptionType", errorType);
                }
                gen.writeEndObject();
            });
            if (error == null) {
                this.logger.atDebug()
                    .addKeyValue("sqlTransaction", arg)
                    .log("Transaction committed");
            } else {
                this.logger.atWarn()
                    .addKeyValue("sqlTransaction", arg)
                    .log("Transaction failed");
            }
        }
    }
}
