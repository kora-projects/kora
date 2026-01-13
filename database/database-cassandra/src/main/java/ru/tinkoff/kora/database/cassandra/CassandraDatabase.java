package ru.tinkoff.kora.database.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.config.ProgrammaticDriverConfigLoaderBuilder;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.common.util.Configurer;
import ru.tinkoff.kora.common.util.TimeUtils;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetry;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetryFactory;

import java.util.Objects;

public final class CassandraDatabase implements CassandraConnectionFactory, Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(CassandraDatabase.class);

    private final CassandraConfig config;
    @Nullable
    private final Configurer<ProgrammaticDriverConfigLoaderBuilder> loaderConfigurer;
    @Nullable
    private final Configurer<CqlSessionBuilder> sessionBuilderConfigurer;
    private final DataBaseTelemetry telemetry;
    private volatile CqlSession cqlSession;

    public CassandraDatabase(CassandraConfig config, @Nullable Configurer<ProgrammaticDriverConfigLoaderBuilder> loaderConfigurer, @Nullable Configurer<CqlSessionBuilder> sessionBuilderConfigurer, DataBaseTelemetryFactory telemetryFactory) {
        this.config = config;
        this.loaderConfigurer = loaderConfigurer;
        this.sessionBuilderConfigurer = sessionBuilderConfigurer;
        this.telemetry = telemetryFactory.get(
            config.telemetry(),
            Objects.requireNonNullElse(config.basic().sessionName(), "cassandra"),
            "cassandra"
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
        logger.debug("CassandraDatabase {} starting...", config.basic().contactPoints());
        var started = System.nanoTime();

        try {
            cqlSession = new CassandraSessionBuilder().build(config, this.loaderConfigurer, this.sessionBuilderConfigurer, this.telemetry.meterRegistry());
        } catch (Exception e) {
            throw new RuntimeException("CassandraDatabase '%s' failed to start, due to: %s".formatted(
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
