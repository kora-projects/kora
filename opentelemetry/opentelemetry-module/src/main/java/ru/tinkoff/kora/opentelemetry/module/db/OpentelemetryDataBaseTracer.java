package ru.tinkoff.kora.opentelemetry.module.db;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.database.common.QueryContext;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTracer;
import ru.tinkoff.kora.opentelemetry.common.OpentelemetryContext;

import javax.annotation.Nullable;

public final class OpentelemetryDataBaseTracer implements DataBaseTracer {
    private final Tracer tracer;
    private final String dbSystem;
    @Nullable
    private final String connectionString;
    private final String user;

    public OpentelemetryDataBaseTracer(Tracer tracer, String dbType, @Nullable String connectionString, String user) {
        this.tracer = tracer;
        this.dbSystem = toDbSystem(dbType);
        this.connectionString = connectionString;
        this.user = user;
    }

    private static String toDbSystem(String type) {
        return switch (type) {
            case "as400", "db2" -> SemanticAttributes.DbSystemValues.DB2;
            case "derby" -> SemanticAttributes.DbSystemValues.DERBY;
            case "h2" -> SemanticAttributes.DbSystemValues.H2;
            case "hsqldb" -> "hsqldb";
            case "mariadb" -> SemanticAttributes.DbSystemValues.MARIADB;
            case "mysql" -> SemanticAttributes.DbSystemValues.MYSQL;
            case "oracle" -> SemanticAttributes.DbSystemValues.ORACLE;
            case "postgresql", "postgres" -> SemanticAttributes.DbSystemValues.POSTGRESQL;
            case "jtds", "microsoft", "sqlserver" -> SemanticAttributes.DbSystemValues.MSSQL;
            default -> SemanticAttributes.DbSystemValues.OTHER_SQL;
        };
    }

    @Override
    public DataBaseSpan createQuerySpan(Context ctx, QueryContext queryContext) {
        var otctx = OpentelemetryContext.get(ctx);
        var builder = this.tracer.spanBuilder(queryContext.operation())
            .setSpanKind(SpanKind.CLIENT)
            .setParent(otctx.getContext())
            .setAttribute(SemanticAttributes.DB_SYSTEM, this.dbSystem)
            .setAttribute(SemanticAttributes.DB_USER, this.user)
            .setAttribute(SemanticAttributes.DB_STATEMENT, queryContext.queryId());
        if (this.connectionString != null) {
            builder.setAttribute(SemanticAttributes.DB_CONNECTION_STRING, this.connectionString);
        }
        var span = builder.startSpan();
        OpentelemetryContext.set(ctx, otctx.add(span));
        return (ex) -> {
            if (ex != null) {
                span.setStatus(StatusCode.ERROR);
            }
            span.end();
            OpentelemetryContext.set(ctx, otctx);
        };
    }

    @Override
    public DataBaseSpan createCallSpan(QueryContext queryContext) {
        var ctx = Context.current();
        var otctx = OpentelemetryContext.get(ctx);
        var builder = this.tracer.spanBuilder(queryContext.operation())
            .setSpanKind(SpanKind.CLIENT)
            .setParent(otctx.getContext())
            .setAttribute(SemanticAttributes.DB_SYSTEM, this.dbSystem)
            .setAttribute(SemanticAttributes.DB_USER, this.user)
            .setAttribute(SemanticAttributes.DB_STATEMENT, queryContext.queryId());

        if (this.connectionString != null) {
            builder.setAttribute(SemanticAttributes.DB_CONNECTION_STRING, this.connectionString);
        }
        var span = builder.startSpan();
        OpentelemetryContext.set(ctx, otctx.add(span));
        return (ex) -> {
            if (ex != null) {
                span.setStatus(StatusCode.ERROR);
            }
            span.end();
            OpentelemetryContext.set(ctx, otctx);
        };
    }
}
