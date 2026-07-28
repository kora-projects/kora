package io.koraframework.database.jdbc.agroal;

import io.agroal.api.AgroalPoolInterceptor;
import io.agroal.api.cache.ConnectionCache;
import io.agroal.api.configuration.AgroalConnectionPoolConfiguration;
import io.agroal.api.configuration.AgroalDataSourceConfiguration;
import io.agroal.api.configuration.supplier.AgroalDataSourceConfigurationSupplier;
import io.agroal.api.security.NamePrincipal;
import io.agroal.api.security.SimplePassword;
import io.agroal.api.transaction.TransactionIntegration;
import io.koraframework.common.Configurer;
import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.database.jdbc.JdbcDatabaseConfig;
import io.koraframework.database.jdbc.JdbcRepository;
import io.koraframework.database.jdbc.exception.UncheckedSqlException;
import org.jspecify.annotations.Nullable;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Properties;

/**
 * <b>Русский</b>: Конфигурация описывающая Agroal соединения к Jdbc базе данных.
 * <hr>
 * <b>English</b>: Configuration describing Agroal connections to the Jdbc database.
 *
 * @see JdbcRepository
 */
@ConfigMapper
public interface AgroalJdbcDatabaseConfig extends JdbcDatabaseConfig {

    default Duration acquisitionTimeout() {
        return Duration.ofSeconds(10);
    }

    default Duration idleValidationTimeout() {
        return Duration.ofMinutes(10);
    }

    default Duration maxLifetime() {
        return Duration.ofMinutes(15);
    }

    default Duration leakTimeout() {
        return Duration.ofSeconds(0);
    }

    default int maxPoolSize() {
        return 10;
    }

    default int minPoolSize() {
        return 0;
    }

    default int initialPoolSize() {
        return 0;
    }

    default boolean trackJdbcResources() {
        return false;
    }

    default boolean recoveryEnable() {
        return false;
    }

    default Properties jdbcProperties() {
        return new Properties();
    }

    static AgroalDataSourceConfiguration toAgroalConfig(AgroalJdbcDatabaseConfig config, @Nullable Configurer<AgroalDataSourceConfiguration> configurer) {
        var dataSourceConfig = new AgroalDataSourceConfigurationSupplier()
            .metricsEnabled(config.telemetry().metrics().driverMetrics());
        var poolConfig = dataSourceConfig.connectionPoolConfiguration()
            .acquisitionTimeout(config.acquisitionTimeout())
            .validationTimeout(config.validationTimeout())
            .enhancedLeakReport(false)
            .connectionCache(ConnectionCache.none())
            .connectionValidator(AgroalConnectionPoolConfiguration.ConnectionValidator.emptyValidator())
            .transactionRequirement(AgroalConnectionPoolConfiguration.TransactionRequirement.OFF)
            .transactionIntegration(TransactionIntegration.none())
            .idleValidationTimeout(config.idleValidationTimeout())
            .maxLifetime(config.maxLifetime())
            .leakTimeout(config.leakTimeout())
            .minSize(config.minPoolSize())
            .maxSize(config.maxPoolSize())
            .initialSize(config.initialPoolSize())
            .recoveryEnable(config.recoveryEnable());
        if (config.schema() != null) {
            poolConfig.addInterceptor(new AgroalPoolInterceptor() {
                @Override
                public void onConnectionAcquire(Connection connection) {
                    try {
                        connection.setSchema(config.schema());
                    } catch (SQLException e) {
                        throw new UncheckedSqlException(e);
                    }
                }
            });
        }
        var connectionFactoryConfig = poolConfig
            .connectionFactoryConfiguration()
            .poolRecovery(config.recoveryEnable())
            .autoCommit(true)
            .trackJdbcResources(config.trackJdbcResources())
            .jdbcUrl(config.jdbcUrl())
            .principal(new NamePrincipal(config.username()))
            .credential(new SimplePassword(config.password()));
        for (var property : config.jdbcProperties().entrySet()) {
            connectionFactoryConfig.jdbcProperty(property.getKey().toString(), property.getValue().toString());
        }
        var agroalConfig = dataSourceConfig.get();
        if (configurer != null) {
            return configurer.configure(agroalConfig);
        }
        return agroalConfig;
    }
}
