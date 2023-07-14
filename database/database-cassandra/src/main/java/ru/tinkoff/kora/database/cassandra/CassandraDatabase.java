package ru.tinkoff.kora.database.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.common.util.ReactorUtils;
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
    public Mono<Void> init() {
        return ReactorUtils.ioMono(() -> {
            try {
                cqlSession = new CassandraSessionBuilder().build(config);
            } catch (Exception e) {
                throw Exceptions.propagate(e);
            }
        });
    }

    @Override
    public Mono<Void> release() {
        return Mono.defer(() -> {
            if (cqlSession != null) {
                return ReactorUtils.ioMono(cqlSession::close);
            } else {
                return Mono.empty();
            }
        });
    }
}
