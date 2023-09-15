package ru.tinkoff.kora.database.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import reactor.core.Exceptions;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetry;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetryFactory;

import java.util.Objects;
import java.util.Optional;

public final class CassandraDatabase implements CassandraConnectionFactory, Lifecycle {
    private final CassandraConfig config;
    private final DataBaseTelemetry telemetry;
    private CqlSession cqlSession;

    public CassandraDatabase(CassandraConfig config, DataBaseTelemetryFactory telemetryFactory) {
        this.config = config;
        this.telemetry = telemetryFactory.get(
            Objects.requireNonNullElse(config.basic().sessionName(), "cassandra"),
            "cassandra",
            "cassandra",
            Optional.ofNullable(config.auth()).map(CassandraConfig.CassandraCredentials::login).orElse("anonymous")
        );
    }

    @Override
    public CqlSession currentSession() {
        return cqlSession;
    }

    @Override
    public DataBaseTelemetry telemetry() {
        return this.telemetry;
    }


    @Override
    public void init() {
        try {
            cqlSession = new CassandraSessionBuilder().build(config);
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public void release() {
        var s = cqlSession;
        if (s != null) {
            s.close();
            cqlSession = null;
        }
    }
}
