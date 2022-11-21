package ru.tinkoff.kora.database.common.telemetry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.database.common.QueryContext;

import javax.annotation.Nullable;

import static ru.tinkoff.kora.logging.common.arg.StructuredArgument.marker;

public class DefaultDataBaseLogger implements DataBaseLogger {
    private final Logger queryLog;
    private final String poolName;

    public DefaultDataBaseLogger(String poolName) {
        this.poolName = poolName;
        this.queryLog = LoggerFactory.getLogger("ru.tinkoff.kora.database.jdbc." + poolName + ".query");
    }

    @Override
    public void logQueryBegin(QueryContext queryContext) {
        if (this.queryLog.isDebugEnabled()) {
            this.queryLog.debug(marker("sqlQuery", gen -> {
                gen.writeStartObject();
                gen.writeStringField("pool", this.poolName);
                gen.writeStringField("queryId", queryContext.queryId());
                gen.writeEndObject();
            }), "Sql query begin");
        }
    }

    @Override
    public void logQueryEnd(long duration, QueryContext queryContext, @Nullable Throwable ex) {
        if (this.queryLog.isDebugEnabled()) {
            this.queryLog.debug(marker("sqlQuery", gen -> {
                gen.writeStartObject();
                gen.writeStringField("pool", this.poolName);
                gen.writeStringField("queryId", queryContext.queryId());
                gen.writeNumberField("duration", duration / 1_000_000);
                gen.writeEndObject();
            }), "Sql query end");
        }
    }
}
