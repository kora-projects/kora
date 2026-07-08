package io.koraframework.database.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.config.ProgrammaticDriverConfigLoaderBuilder;
import io.koraframework.application.graph.Lifecycle;
import io.koraframework.application.graph.Wrapped;
import io.koraframework.common.Configurer;
import io.koraframework.common.util.TimeUtils;
import io.koraframework.database.cassandra.util.CassandraSessionBuilderUtils;
import io.koraframework.database.common.telemetry.DatabaseTelemetry;
import io.koraframework.database.common.telemetry.DatabaseTelemetryFactory;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class CassandraSession implements CassandraExecutor, Wrapped<CqlSession>, Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(CassandraSession.class);

    private final CassandraConfig config;
    @Nullable
    private final Configurer<ProgrammaticDriverConfigLoaderBuilder> loaderConfigurer;
    @Nullable
    private final Configurer<CqlSessionBuilder> sessionBuilderConfigurer;
    private final DatabaseTelemetry telemetry;

    private volatile CqlSession cqlSession;

    public CassandraSession(CassandraConfig config,
                            DatabaseTelemetryFactory telemetryFactory,
                            @Nullable Configurer<ProgrammaticDriverConfigLoaderBuilder> loaderConfigurer,
                            @Nullable Configurer<CqlSessionBuilder> sessionBuilderConfigurer) {
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
    public DatabaseTelemetry telemetry() {
        return this.telemetry;
    }

    @Override
    public CqlSession value() {
        return this.cqlSession;
    }

    @Override
    public void init() {
        logger.debug("CassandraDataSource {} starting...", config.basic().contactPoints());
        var started = System.nanoTime();

        try {
            cqlSession = CassandraSessionBuilderUtils.build(config, this.loaderConfigurer, this.sessionBuilderConfigurer, this.telemetry.meterRegistry());
        } catch (Exception e) {
            throw new RuntimeException("CassandraDataSource '%s' failed to start, due to: %s".formatted(
                config.basic().contactPoints(), e.getMessage()), e);
        }

        logger.info("CassandraDataSource {} started in {}", config.basic().contactPoints(), TimeUtils.tookForLogging(started));
    }

    @Override
    public void release() {
        var s = cqlSession;
        if (s != null) {
            logger.debug("CassandraDataSource '{}' stopping...", config.basic().contactPoints());
            var started = System.nanoTime();

            s.close();
            cqlSession = null;

            logger.info("CassandraDataSource '{}' stopped in {}", config.basic().contactPoints(), TimeUtils.tookForLogging(started));
        }
    }
}
