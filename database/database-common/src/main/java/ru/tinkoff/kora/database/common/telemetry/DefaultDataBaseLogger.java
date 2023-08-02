package ru.tinkoff.kora.database.common.telemetry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import ru.tinkoff.kora.database.common.QueryContext;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;

import javax.annotation.Nullable;

public class DefaultDataBaseLogger implements DataBaseLogger {

    private final Logger log;
    private final String poolName;

    public DefaultDataBaseLogger(String poolName) {
        this.poolName = poolName;
        this.log = LoggerFactory.getLogger("ru.tinkoff.kora.database." + poolName + ".query");
    }

    @Override
    public boolean isEnabled() {
        return this.log.isDebugEnabled();
    }

    @Override
    public void logQueryBegin(QueryContext queryContext) {
        if (log.isTraceEnabled()) {
            log.trace(queryBeginMarker(queryContext), "SQL executing for pool '{}':\n{}", this.poolName, queryContext.sql());
        } else if (log.isDebugEnabled()) {
            log.debug(queryBeginMarker(queryContext), "SQL executing for pool '{}'", this.poolName);
        }
    }

    @Override
    public void logQueryEnd(long processingTime, QueryContext queryContext, @Nullable Throwable ex) {
        if (log.isTraceEnabled()) {
            log.trace(queryEndMarker(processingTime, queryContext), "SQL executed for pool '{}':\n{}", this.poolName, queryContext.sql());
        } else if (log.isDebugEnabled()) {
            log.debug(queryEndMarker(processingTime, queryContext), "SQL executed for pool '{}'", this.poolName);
        }
    }

    private Marker queryBeginMarker(QueryContext queryContext) {
        return StructuredArgument.marker("sqlQuery", gen -> {
            gen.writeStartObject();
            gen.writeStringField("pool", this.poolName);
            gen.writeStringField("queryId", queryContext.queryId());
            gen.writeEndObject();
        });
    }

    private Marker queryEndMarker(long processingTime, QueryContext queryContext) {
        return StructuredArgument.marker("sqlQuery", gen -> {
            gen.writeStartObject();
            gen.writeStringField("pool", this.poolName);
            gen.writeStringField("queryId", queryContext.queryId());
            gen.writeNumberField("processingTime", processingTime / 1_000_000);
            gen.writeEndObject();
        });
    }

}
