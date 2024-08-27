package ru.tinkoff.kora.camunda.engine;

import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.jobexecutor.JobExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.camunda.engine.transaction.CamundaTransactionManager;
import ru.tinkoff.kora.camunda.engine.transaction.JdbcCamundaTransactionManager;
import ru.tinkoff.kora.database.common.telemetry.DefaultDataBaseTelemetryFactory;
import ru.tinkoff.kora.database.jdbc.$JdbcDatabaseConfig_ConfigValueExtractor;
import ru.tinkoff.kora.database.jdbc.JdbcDatabase;
import ru.tinkoff.kora.telemetry.common.*;
import ru.tinkoff.kora.test.postgres.PostgresParams;
import ru.tinkoff.kora.test.postgres.PostgresTestContainer;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

@ExtendWith({PostgresTestContainer.class})
public class KoraProcessEngineTests implements CamundaEngineModule {

    @Test
    void initTwoStage(PostgresParams params) {
        withDatabase(params, jdbc -> {
            var config = new $CamundaEngineConfig_ConfigValueExtractor.CamundaEngineConfig_Impl(
                new CamundaEngineConfig.ParallelInitConfig() {},
                "camunda-license.txt",
                $CamundaEngineConfig_JobExecutorConfig_ConfigValueExtractor.DEFAULTS,
                new $CamundaEngineConfig_CamundaTelemetryConfig_ConfigValueExtractor.CamundaTelemetryConfig_Impl(
                    $CamundaEngineConfig_CamundaEngineLogConfig_ConfigValueExtractor.DEFAULTS,
                    new $TelemetryConfig_TracingConfig_ConfigValueExtractor.TracingConfig_Impl(true),
                    new $TelemetryConfig_MetricsConfig_ConfigValueExtractor.MetricsConfig_Impl(true, TelemetryConfig.MetricsConfig.DEFAULT_SLO),
                    true,
                    true
                ),
                new $CamundaEngineConfig_FilterConfig_ConfigValueExtractor.FilterConfig_Impl("All tasks"),
                new $CamundaEngineConfig_DeploymentConfig_ConfigValueExtractor.DeploymentConfig_Impl(null, "MyDep", false, List.of("bpm")),
                new $CamundaEngineConfig_AdminConfig_ConfigValueExtractor.AdminConfig_Impl("admin", "admin", null, null, null)
            );

            KoraDelegateWrapperFactory koraDelegateWrapperFactory = koraJavaDelegateTelemetryWrapper(null, null);
            JobExecutor jobExecutor = camundaEngineKoraJobExecutor(config);

            CamundaDataSource camundaDataSource = new CamundaDataSource() {

                @Override
                public CamundaTransactionManager transactionManager() {
                    return new JdbcCamundaTransactionManager(jdbc.value());
                }

                @Override
                public DataSource dataSource() {
                    return jdbc.value();
                }
            };

            ProcessEngineConfiguration koraProcessEngineConfiguration = camundaEngineKoraProcessEngineConfiguration(
                jobExecutor,
                camundaEngineKoraTelemetryRegistry(null),
                camundaEngineIdGenerator(),
                camundaEngineKoraExpressionManager(camundaEngineKoraELResolver(koraDelegateWrapperFactory, All.of(), All.of())),
                camundaEngineKoraArtifactFactory(koraDelegateWrapperFactory, All.of(), All.of()),
                All.of(),
                camundaDataSource,
                config,
                camundaEngineKoraComponentResolverFactory(koraDelegateWrapperFactory, All.of(), All.of()),
                camundaEnginePackageVersion()
            );

            KoraProcessEngine koraProcessEngine = camundaEngineKoraProcessEngine(koraProcessEngineConfiguration,
                config,
                All.of(
                    camundaEngineKoraProcessEngineTwoStageCamundaConfigurator(koraProcessEngineConfiguration, config, jobExecutor),
                    camundaEngineKoraAdminUserConfigurator(config, camundaDataSource),
                    camundaEngineKoraLicenseKeyConfigurator(config, camundaEnginePackageVersion()),
                    camundaEngineKoraFilterAllTaskConfigurator(config),
                    camundaEngineKoraResourceDeploymentConfigurator(config)
                ));
            try {
                koraProcessEngine.init();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            } finally {
                koraProcessEngine.release();
            }
        });
    }

    @Test
    void initOneStage(PostgresParams params) {
        withDatabase(params, jdbc -> {
            var config = new $CamundaEngineConfig_ConfigValueExtractor.CamundaEngineConfig_Impl(
                new CamundaEngineConfig.ParallelInitConfig() {
                    @Override
                    public boolean enabled() {
                        return false;
                    }
                },
                "camunda-license.txt",
                $CamundaEngineConfig_JobExecutorConfig_ConfigValueExtractor.DEFAULTS,
                new $CamundaEngineConfig_CamundaTelemetryConfig_ConfigValueExtractor.CamundaTelemetryConfig_Impl(
                    $CamundaEngineConfig_CamundaEngineLogConfig_ConfigValueExtractor.DEFAULTS,
                    new $TelemetryConfig_TracingConfig_ConfigValueExtractor.TracingConfig_Impl(true),
                    new $TelemetryConfig_MetricsConfig_ConfigValueExtractor.MetricsConfig_Impl(true, TelemetryConfig.MetricsConfig.DEFAULT_SLO),
                    true,
                    true
                ),
                new $CamundaEngineConfig_FilterConfig_ConfigValueExtractor.FilterConfig_Impl("All tasks"),
                new $CamundaEngineConfig_DeploymentConfig_ConfigValueExtractor.DeploymentConfig_Impl(null, "MyDep", false, List.of("bpm")),
                new $CamundaEngineConfig_AdminConfig_ConfigValueExtractor.AdminConfig_Impl("admin", "admin", null, null, null)
            );

            KoraDelegateWrapperFactory koraDelegateWrapperFactory = koraJavaDelegateTelemetryWrapper(null, null);
            JobExecutor jobExecutor = camundaEngineKoraJobExecutor(config);

            CamundaDataSource camundaDataSource = new CamundaDataSource() {

                @Override
                public CamundaTransactionManager transactionManager() {
                    return new JdbcCamundaTransactionManager(jdbc.value());
                }

                @Override
                public DataSource dataSource() {
                    return jdbc.value();
                }
            };

            ProcessEngineConfiguration koraProcessEngineConfiguration = camundaEngineKoraProcessEngineConfiguration(
                jobExecutor,
                camundaEngineKoraTelemetryRegistry(null),
                camundaEngineIdGenerator(),
                camundaEngineKoraExpressionManager(camundaEngineKoraELResolver(koraDelegateWrapperFactory, All.of(), All.of())),
                camundaEngineKoraArtifactFactory(koraDelegateWrapperFactory, All.of(), All.of()),
                All.of(),
                camundaDataSource,
                config,
                camundaEngineKoraComponentResolverFactory(koraDelegateWrapperFactory, All.of(), All.of()),
                camundaEnginePackageVersion()
            );

            KoraProcessEngine koraProcessEngine = camundaEngineKoraProcessEngine(koraProcessEngineConfiguration,
                config,
                All.of(
                    camundaEngineKoraAdminUserConfigurator(config, camundaDataSource),
                    camundaEngineKoraLicenseKeyConfigurator(config, camundaEnginePackageVersion()),
                    camundaEngineKoraFilterAllTaskConfigurator(config),
                    camundaEngineKoraResourceDeploymentConfigurator(config)
                ));
            try {
                koraProcessEngine.init();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            } finally {
                koraProcessEngine.release();
            }
        });
    }

    private static void withDatabase(PostgresParams params, Consumer<JdbcDatabase> consumer) {
        var config = new $JdbcDatabaseConfig_ConfigValueExtractor.JdbcDatabaseConfig_Impl(
            params.user(),
            params.password(),
            params.jdbcUrl(),
            "testPool",
            null,
            Duration.ofMillis(5000L),
            Duration.ofMillis(5000L),
            Duration.ofMillis(5000L),
            Duration.ofMillis(5000L),
            Duration.ofMillis(5000L),
            10,
            1,
            Duration.ofMillis(5000L),
            false,
            new Properties(),
            new $TelemetryConfig_ConfigValueExtractor.TelemetryConfig_Impl(
                new $TelemetryConfig_LogConfig_ConfigValueExtractor.LogConfig_Impl(true),
                new $TelemetryConfig_TracingConfig_ConfigValueExtractor.TracingConfig_Impl(true),
                new $TelemetryConfig_MetricsConfig_ConfigValueExtractor.MetricsConfig_Impl(true, TelemetryConfig.MetricsConfig.DEFAULT_SLO)
            )
        );

        var db = new JdbcDatabase(config, new DefaultDataBaseTelemetryFactory(null, null, null));
        try {
            db.init();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        try {
            consumer.accept(db);
        } finally {
            db.release();
        }
    }
}
