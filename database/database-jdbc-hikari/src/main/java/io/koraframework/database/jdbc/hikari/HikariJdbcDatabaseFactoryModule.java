package io.koraframework.database.jdbc.hikari;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.koraframework.common.Configurer;
import io.koraframework.common.annotation.Tag;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.database.common.telemetry.DatabaseTelemetryFactory;
import io.koraframework.database.jdbc.JdbcDataSource;
import org.jspecify.annotations.Nullable;

public class HikariJdbcDatabaseFactoryModule {

    private final String configPath;

    public HikariJdbcDatabaseFactoryModule(String configPath) {
        this.configPath = configPath;
    }

    @Tag(Tag.Factory.class)
    public HikariJdbcDatabaseConfig config(Config config, ConfigValueMapper<HikariJdbcDatabaseConfig> mapper) {
        return mapper.mapOrThrow(config.get(this.configPath));
    }

    @Tag(Tag.Factory.class)
    public JdbcDataSource jdbcDataSource(@Tag(Tag.Factory.class) HikariJdbcDatabaseConfig config,
                                         DatabaseTelemetryFactory telemetryFactory,
                                         @Tag(Tag.Factory.class) @Nullable Configurer<HikariConfig> configurer) {
        var jdbcUrl = config.jdbcUrl();
        var jdbcDatabase = jdbcUrl.substring(5, jdbcUrl.indexOf(":", 5));
        var telemetry = telemetryFactory.get(config.telemetry(), config.poolName(), jdbcDatabase);

        var dataSource = new HikariDataSource(HikariJdbcDatabaseConfig.toHikariConfig(config, configurer));
        if (config.telemetry().metrics().driverMetrics()) {
            dataSource.setMetricRegistry(telemetry.meterRegistry());
        }

        return new JdbcDataSource(dataSource, config, (_, _, _) -> telemetry, dataSource::close);
    }
}
