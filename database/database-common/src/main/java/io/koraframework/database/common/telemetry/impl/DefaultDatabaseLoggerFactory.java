package io.koraframework.database.common.telemetry.impl;

import io.koraframework.database.common.*;
import io.koraframework.logging.common.arg.*;
import org.jspecify.annotations.*;
import org.slf4j.*;
import tools.jackson.core.*;
import java.util.function.Supplier;

public class DefaultDatabaseLoggerFactory {

    public static final DefaultDatabaseLoggerFactory INSTANCE = new DefaultDatabaseLoggerFactory();

    public DefaultDatabaseLogger create(DefaultDatabaseTelemetry.TelemetryContext context) {
        var logger = LoggerFactory.getLogger("io.koraframework.database." + context.poolName() + ".query");
        return new DefaultDatabaseLogger(logger, context);
    }

    public static class DefaultDatabaseLogger {

        protected final Logger logger;
        protected final DefaultDatabaseTelemetry.TelemetryContext context;
        private static final ThreadLocal<LogBeginContext> BEGIN_CTX = ThreadLocal.withInitial(new LogBeginContextSupplier());
        private static final ThreadLocal<LogEndContext> END_CTX = ThreadLocal.withInitial(new LogEndContextSupplier());
        private static final StructuredArgumentWriter BEGIN_SERIALIZER = new BeginSerializer();
        private static final StructuredArgumentWriter END_SERIALIZER = new EndSerializer();

        public DefaultDatabaseLogger(Logger logger, DefaultDatabaseTelemetry.TelemetryContext context) {
            this.logger = logger;
            this.context = context;
        }

        public void logQueryBegin(QueryContext query) {
            if (!this.logger.isDebugEnabled()) {
                return;
            }

            final LogBeginContext ctx = BEGIN_CTX.get();
            ctx.poolName = this.context.poolName();
            ctx.query = query;
            ctx.isTraceEnabled = this.logger.isTraceEnabled();

            try {
                this.logger.atDebug()
                    .addKeyValue("sqlQuery", StructuredArgument.value(BEGIN_SERIALIZER))
                    .log("Executing query");
            } finally {
                ctx.poolName = null;
                ctx.query = null;
            }
        }

        public void logQueryEnd(QueryContext query, @Nullable Throwable error, long processingTimeNanos) {
            final boolean isDebug = this.logger.isDebugEnabled();
            if (error == null && !isDebug) {
                return;
            }
            if (error != null && !this.logger.isWarnEnabled()) {
                return;
            }

            String errorType = null;
            if (error != null) {
                errorType = error.getClass().getCanonicalName();
                if (errorType == null) {
                    errorType = error.getClass().getName();
                }
            }

            final LogEndContext ctx = END_CTX.get();
            ctx.poolName = this.context.poolName();
            ctx.query = query;
            ctx.processingTimeNanos = processingTimeNanos;
            ctx.errorType = errorType;
            ctx.isTraceEnabled = this.logger.isTraceEnabled();

            try {
                var arg = StructuredArgument.value(END_SERIALIZER);
                if (error == null) {
                    this.logger.atDebug()
                        .addKeyValue("sqlQuery", arg)
                        .log("Query executed");
                } else {
                    this.logger.atWarn()
                        .addKeyValue("sqlQuery", arg)
                        .log("Query failed");
                }
            } finally {
                ctx.poolName = null;
                ctx.query = null;
                ctx.errorType = null;
            }
        }

        private static final class LogBeginContext {
            String poolName;
            QueryContext query;
            boolean isTraceEnabled;
        }

        private static final class LogEndContext {
            String poolName;
            QueryContext query;
            long processingTimeNanos;
            String errorType;
            boolean isTraceEnabled;
        }

        private static final class BeginSerializer implements StructuredArgumentWriter {
            @Override
            public void writeTo(JsonGenerator gen) {
                LogBeginContext ctx = BEGIN_CTX.get();
                gen.writeStartObject();
                gen.writeStringProperty("pool", ctx.poolName);
                gen.writeStringProperty("operation", ctx.query.operation());
                gen.writeStringProperty("queryId", ctx.query.queryId());
                if (ctx.isTraceEnabled) {
                    gen.writeStringProperty("sql", ctx.query.sql());
                }
                gen.writeEndObject();
            }
        }

        private static final class EndSerializer implements StructuredArgumentWriter {
            @Override
            public void writeTo(JsonGenerator gen) {
                LogEndContext ctx = END_CTX.get();
                gen.writeStartObject();
                gen.writeStringProperty("pool", ctx.poolName);
                gen.writeStringProperty("operation", ctx.query.operation());
                gen.writeStringProperty("queryId", ctx.query.queryId());
                gen.writeNumberProperty("processingTime", ctx.processingTimeNanos / 1_000_000);
                if (ctx.errorType != null) {
                    gen.writeStringProperty("exceptionType", ctx.errorType);
                }
                if (ctx.isTraceEnabled) {
                    gen.writeStringProperty("sql", ctx.query.sql());
                }
                gen.writeEndObject();
            }
        }

        private static final class LogBeginContextSupplier implements Supplier<LogBeginContext> {
            @Override
            public LogBeginContext get() {
                return new LogBeginContext();
            }
        }

        private static final class LogEndContextSupplier implements Supplier<LogEndContext> {
            @Override
            public LogEndContext get() {
                return new LogEndContext();
            }
        }
    }
}
