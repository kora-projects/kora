package ru.tinkoff.kora.camunda.engine.bpmn;

import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.jobexecutor.JobExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.camunda.engine.bpmn.transaction.CamundaTransactionManager;
import ru.tinkoff.kora.camunda.engine.bpmn.transaction.JdbcCamundaTransactionManager;
import ru.tinkoff.kora.database.common.telemetry.*;
import ru.tinkoff.kora.database.jdbc.$JdbcDatabaseConfig_ConfigValueExtractor;
import ru.tinkoff.kora.database.jdbc.JdbcDatabase;
import ru.tinkoff.kora.telemetry.common.$TelemetryConfig_MetricsConfig_ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.$TelemetryConfig_TracingConfig_ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;
import ru.tinkoff.kora.test.postgres.PostgresParams;
import ru.tinkoff.kora.test.postgres.PostgresTestContainer;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

@ExtendWith({PostgresTestContainer.class})
public class KoraProcessEngineTests implements CamundaEngineBpmnModule {

    @Test
    void initTwoStage(PostgresParams params) {
        withDatabase(params, jdbc -> {
            var config = new $CamundaEngineBpmnConfig_ConfigValueExtractor.CamundaEngineBpmnConfig_Impl(
                new CamundaEngineBpmnConfig.ParallelInitConfig() {},
                $CamundaEngineBpmnConfig_JobExecutorConfig_ConfigValueExtractor.DEFAULTS,
                new $CamundaEngineBpmnConfig_DeploymentConfig_ConfigValueExtractor.DeploymentConfig_Impl(null, "MyDep", false, List.of("bpm"), null),
                new $CamundaEngineBpmnConfig_AdminConfig_ConfigValueExtractor.AdminConfig_Impl("admin", "admin", null, null, null),
                new $CamundaEngineBpmnConfig_CamundaTelemetryConfig_ConfigValueExtractor.CamundaTelemetryConfig_Impl(
                    $CamundaEngineBpmnConfig_CamundaEngineLogConfig_ConfigValueExtractor.DEFAULTS,
                    new $TelemetryConfig_TracingConfig_ConfigValueExtractor.TracingConfig_Impl(true, Map.of()),
                    true,
                    new $TelemetryConfig_MetricsConfig_ConfigValueExtractor.MetricsConfig_Impl(true, TelemetryConfig.MetricsConfig.DEFAULT_SLO, Map.of())
                )
            );

            KoraDelegateWrapperFactory koraDelegateWrapperFactory = koraJavaDelegateTelemetryWrapper(null, null);
            JobExecutor jobExecutor = camundaEngineBpmnKoraJobExecutor(config);

            CamundaEngineDataSource camundaEngineDataSource = new CamundaEngineDataSource() {

                @Override
                public CamundaTransactionManager transactionManager() {
                    return new JdbcCamundaTransactionManager(jdbc.value());
                }

                @Override
                public DataSource dataSource() {
                    return jdbc.value();
                }
            };

            ProcessEngineConfiguration koraProcessEngineConfiguration = camundaEngineBpmnKoraProcessEngineConfiguration(
                jobExecutor,
                camundaEngineBpmnKoraTelemetryRegistry(null),
                camundaEngineBpmnIdGenerator(),
                camundaEngineBpmnKoraExpressionManager(camundaEngineBpmnKoraELResolver(koraDelegateWrapperFactory, All.of(), All.of())),
                camundaEngineBpmnKoraArtifactFactory(koraDelegateWrapperFactory, All.of(), All.of()),
                All.of(),
                camundaEngineDataSource,
                config,
                camundaEngineBpmnKoraComponentResolverFactory(koraDelegateWrapperFactory, All.of(), All.of()),
                camundaEngineBpmnPackageVersion()
            );

            KoraProcessEngine koraProcessEngine = camundaEngineBpmnKoraProcessEngine(koraProcessEngineConfiguration,
                config,
                All.of(
                    camundaEngineBpmnKoraProcessEngineTwoStageCamundaConfigurator(koraProcessEngineConfiguration, config, jobExecutor),
                    camundaEngineBpmnKoraAdminUserConfigurator(config, camundaEngineDataSource),
                    camundaEngineBpmnKoraResourceDeploymentConfigurator(config)
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
            var config = new $CamundaEngineBpmnConfig_ConfigValueExtractor.CamundaEngineBpmnConfig_Impl(
                new CamundaEngineBpmnConfig.ParallelInitConfig() {
                    @Override
                    public boolean enabled() {
                        return false;
                    }
                },
                $CamundaEngineBpmnConfig_JobExecutorConfig_ConfigValueExtractor.DEFAULTS,
                new $CamundaEngineBpmnConfig_DeploymentConfig_ConfigValueExtractor.DeploymentConfig_Impl(null, "MyDep", false, List.of("bpm"), null),
                new $CamundaEngineBpmnConfig_AdminConfig_ConfigValueExtractor.AdminConfig_Impl("admin", "admin", null, null, null),
                new $CamundaEngineBpmnConfig_CamundaTelemetryConfig_ConfigValueExtractor.CamundaTelemetryConfig_Impl(
                    $CamundaEngineBpmnConfig_CamundaEngineLogConfig_ConfigValueExtractor.DEFAULTS,
                    new $TelemetryConfig_TracingConfig_ConfigValueExtractor.TracingConfig_Impl(true, Map.of()),
                    true,
                    new $TelemetryConfig_MetricsConfig_ConfigValueExtractor.MetricsConfig_Impl(true, TelemetryConfig.MetricsConfig.DEFAULT_SLO, Map.of())
                )
            );

            KoraDelegateWrapperFactory koraDelegateWrapperFactory = koraJavaDelegateTelemetryWrapper(null, null);
            JobExecutor jobExecutor = camundaEngineBpmnKoraJobExecutor(config);

            CamundaEngineDataSource camundaEngineDataSource = new CamundaEngineDataSource() {

                @Override
                public CamundaTransactionManager transactionManager() {
                    return new JdbcCamundaTransactionManager(jdbc.value());
                }

                @Override
                public DataSource dataSource() {
                    return jdbc.value();
                }
            };

            ProcessEngineConfiguration koraProcessEngineConfiguration = camundaEngineBpmnKoraProcessEngineConfiguration(
                jobExecutor,
                camundaEngineBpmnKoraTelemetryRegistry(null),
                camundaEngineBpmnIdGenerator(),
                camundaEngineBpmnKoraExpressionManager(camundaEngineBpmnKoraELResolver(koraDelegateWrapperFactory, All.of(), All.of())),
                camundaEngineBpmnKoraArtifactFactory(koraDelegateWrapperFactory, All.of(), All.of()),
                All.of(),
                camundaEngineDataSource,
                config,
                camundaEngineBpmnKoraComponentResolverFactory(koraDelegateWrapperFactory, All.of(), All.of()),
                camundaEngineBpmnPackageVersion()
            );

            KoraProcessEngine koraProcessEngine = camundaEngineBpmnKoraProcessEngine(koraProcessEngineConfiguration,
                config,
                All.of(
                    camundaEngineBpmnKoraAdminUserConfigurator(config, camundaEngineDataSource),
                    camundaEngineBpmnKoraResourceDeploymentConfigurator(config)
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
            new $DatabaseTelemetryConfig_ConfigValueExtractor.DatabaseTelemetryConfig_Impl(
                new $DatabaseTelemetryConfig_DatabaseLogConfig_ConfigValueExtractor.DatabaseLogConfig_Impl(true),
                new $DatabaseTelemetryConfig_DatabaseTracingConfig_ConfigValueExtractor.DatabaseTracingConfig_Impl(true, Map.of()),
                new $DatabaseTelemetryConfig_DatabaseMetricsConfig_ConfigValueExtractor.DatabaseMetricsConfig_Impl(true, true, TelemetryConfig.MetricsConfig.DEFAULT_SLO, Map.of())
            )
        );

        var db = new JdbcDatabase(config, DataBaseTelemetryFactory.NOOP);
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
