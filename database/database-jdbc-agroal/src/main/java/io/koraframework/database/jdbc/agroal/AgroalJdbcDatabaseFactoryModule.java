package io.koraframework.database.jdbc.agroal;

import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.AgroalDataSourceConfiguration;
import io.koraframework.common.Configurer;
import io.koraframework.common.annotation.Tag;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.database.common.telemetry.DatabaseTelemetryFactory;
import io.koraframework.database.jdbc.JdbcDataSource;
import org.jspecify.annotations.Nullable;

import java.sql.SQLException;

public class AgroalJdbcDatabaseFactoryModule {

    private final String configPath;

    public AgroalJdbcDatabaseFactoryModule(String configPath) {
        this.configPath = configPath;
    }

    @Tag(Tag.Factory.class)
    public AgroalJdbcDatabaseConfig config(Config config, ConfigValueMapper<AgroalJdbcDatabaseConfig> mapper) {
        return mapper.mapOrThrow(config.get(this.configPath));
    }

    @Tag(Tag.Factory.class)
    public JdbcDataSource jdbcDataSource(@Tag(Tag.Factory.class) AgroalJdbcDatabaseConfig config,
                                         DatabaseTelemetryFactory telemetryFactory,
                                         @Tag(Tag.Factory.class) @Nullable Configurer<AgroalDataSourceConfiguration> configurer) throws SQLException {
        var jdbcUrl = config.jdbcUrl();
        var jdbcDatabase = jdbcUrl.substring(5, jdbcUrl.indexOf(":", 5));
        var telemetry = telemetryFactory.get(config.telemetry(), config.poolName(), jdbcDatabase);

        var dataSource = AgroalDataSource.from(AgroalJdbcDatabaseConfig.toAgroalConfig(config, configurer));

        return new JdbcDataSource(dataSource, config, (_, _, _) -> telemetry, dataSource::close);
    }
}
