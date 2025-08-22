package ru.tinkoff.kora.database.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.common.util.TimeUtils;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetry;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetryFactory;

import java.util.Objects;
import java.util.Optional;

public final class CassandraDatabase implements CassandraConnectionFactory, Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(CassandraDatabase.class);

    private final CassandraConfig config;
    private final DataBaseTelemetry telemetry;
    @Nullable
    private final CassandraConfigurer configurer;
    private volatile CqlSession cqlSession;

    @Deprecated
    public CassandraDatabase(CassandraConfig config, DataBaseTelemetryFactory telemetryFactory) {
        this(config, null, telemetryFactory);
    }

    public CassandraDatabase(CassandraConfig config, @Nullable CassandraConfigurer configurer, DataBaseTelemetryFactory telemetryFactory) {
        this.config = config;
        this.configurer = configurer;
        this.telemetry = Objects.requireNonNullElse(telemetryFactory.get(
            config.telemetry(),
            Objects.requireNonNullElse(config.basic().sessionName(), "cassandra"),
            "cassandra",
            "cassandra",
            Optional.ofNullable(config.auth()).map(CassandraConfig.CassandraCredentials::login).orElse("anonymous")
        ), DataBaseTelemetryFactory.EMPTY);
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
        logger.debug("CassandraDatabase {} starting...", config.basic().contactPoints());
        var started = System.nanoTime();

        try {
            cqlSession = new CassandraSessionBuilder().build(config, configurer, telemetry);
        } catch (Exception e) {
            throw new IllegalStateException("CassandraDatabase '%s' failed to start, due to: %s".formatted(
                config.basic().contactPoints(), e.getMessage()), e);
        }

        logger.info("CassandraDatabase {} started in {}", config.basic().contactPoints(), TimeUtils.tookForLogging(started));
    }

    @Override
    public void release() {
        var s = cqlSession;
        if (s != null) {
            logger.debug("CassandraDatabase '{}' stopping...", config.basic().contactPoints());
            var started = System.nanoTime();

            s.close();
            cqlSession = null;

            logger.info("CassandraDatabase '{}' stopped in {}", config.basic().contactPoints(), TimeUtils.tookForLogging(started));
        }
    }
}
