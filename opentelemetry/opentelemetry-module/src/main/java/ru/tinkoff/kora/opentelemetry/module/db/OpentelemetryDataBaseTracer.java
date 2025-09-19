package ru.tinkoff.kora.opentelemetry.module.db;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.database.common.QueryContext;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTracer;
import ru.tinkoff.kora.opentelemetry.common.OpentelemetryContext;

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
            case "as400", "db2" -> DbIncubatingAttributes.DbSystemNameIncubatingValues.IBM_DB2;
            case "derby" -> DbIncubatingAttributes.DbSystemNameIncubatingValues.DERBY;
            case "h2", "h2database" -> DbIncubatingAttributes.DbSystemNameIncubatingValues.H2DATABASE;
            case "hsqldb" -> DbIncubatingAttributes.DbSystemNameIncubatingValues.HSQLDB;
            case "mariadb" -> DbIncubatingAttributes.DbSystemNameIncubatingValues.MARIADB;
            case "mysql" -> DbIncubatingAttributes.DbSystemNameIncubatingValues.MYSQL;
            case "sqlite" -> DbIncubatingAttributes.DbSystemNameIncubatingValues.SQLITE;
            case "oracle" -> DbIncubatingAttributes.DbSystemNameIncubatingValues.ORACLE_DB;
            case "postgresql", "postgres" -> DbIncubatingAttributes.DbSystemNameIncubatingValues.POSTGRESQL;
            case "cassandra" -> DbIncubatingAttributes.DbSystemNameIncubatingValues.CASSANDRA;
            case "clickhouse" -> DbIncubatingAttributes.DbSystemNameIncubatingValues.CLICKHOUSE;
            case "cockroachdb" -> DbIncubatingAttributes.DbSystemNameIncubatingValues.COCKROACHDB;
            case "couchbase" -> DbIncubatingAttributes.DbSystemNameIncubatingValues.COUCHBASE;
            case "couchdb" -> DbIncubatingAttributes.DbSystemNameIncubatingValues.COUCHDB;
            case "hive" -> DbIncubatingAttributes.DbSystemNameIncubatingValues.HIVE;
            case "influxdb" -> DbIncubatingAttributes.DbSystemNameIncubatingValues.INFLUXDB;
            case "trino" -> DbIncubatingAttributes.DbSystemNameIncubatingValues.TRINO;
            case "jtds", "microsoft", "sqlserver" -> DbIncubatingAttributes.DbSystemNameIncubatingValues.MICROSOFT_SQL_SERVER;
            default -> DbIncubatingAttributes.DbSystemNameIncubatingValues.OTHER_SQL;
        };
    }

    @Override
    public DataBaseSpan createQuerySpan(Context ctx, QueryContext queryContext) {
        var otctx = OpentelemetryContext.get(ctx);
        var builder = this.tracer.spanBuilder(queryContext.operation())
            .setSpanKind(SpanKind.CLIENT)
            .setParent(otctx.getContext())
            .setAttribute(DbIncubatingAttributes.DB_SYSTEM, this.dbSystem)
            .setAttribute(DbIncubatingAttributes.DB_USER, this.user)
            .setAttribute(DbIncubatingAttributes.DB_STATEMENT, queryContext.queryId());
        if (this.connectionString != null) {
            @SuppressWarnings("deprecation")
            var ignore = builder.setAttribute(DbIncubatingAttributes.DB_CONNECTION_STRING, this.connectionString);
        }
        var span = builder.startSpan();
        OpentelemetryContext.set(ctx, otctx.add(span));
        return (ex) -> {
            if (ex != null) {
                span.recordException(ex);
                span.setStatus(StatusCode.ERROR);
            } else {
                span.setStatus(StatusCode.OK);
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
            .setAttribute(DbIncubatingAttributes.DB_SYSTEM, this.dbSystem)
            .setAttribute(DbIncubatingAttributes.DB_USER, this.user)
            .setAttribute(DbIncubatingAttributes.DB_STATEMENT, queryContext.queryId());

        if (this.connectionString != null) {
            @SuppressWarnings("deprecation")
            var ignore = builder.setAttribute(DbIncubatingAttributes.DB_CONNECTION_STRING, this.connectionString);
        }
        var span = builder.startSpan();
        OpentelemetryContext.set(ctx, otctx.add(span));
        return (ex) -> {
            if (ex != null) {
                span.recordException(ex);
                span.setStatus(StatusCode.ERROR);
            } else {
                span.setStatus(StatusCode.OK);
            }
            span.end();
            OpentelemetryContext.set(ctx, otctx);
        };
    }
}
