package io.koraframework.bpmn.operaton.engine;

import io.koraframework.application.graph.All;
import io.koraframework.bpmn.operaton.engine.transaction.JdbcOperatonTransactionManager;
import io.koraframework.bpmn.operaton.engine.transaction.OperatonTransactionManager;
import io.koraframework.database.common.telemetry.*;
import io.koraframework.database.common.telemetry.impl.NoopDatabaseMetricsFactory;
import io.koraframework.database.common.telemetry.impl.NoopDatabaseTelemetryFactory;
import io.koraframework.database.jdbc.$JdbcDatabaseConfig_ConfigValueExtractor;
import io.koraframework.database.jdbc.JdbcDatabase;
import io.koraframework.telemetry.common.$TelemetryConfig_MetricsConfig_ConfigValueExtractor;
import io.koraframework.telemetry.common.$TelemetryConfig_TracingConfig_ConfigValueExtractor;
import io.koraframework.telemetry.common.TelemetryConfig;
import io.koraframework.test.postgres.PostgresParams;
import io.koraframework.test.postgres.PostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.operaton.bpm.engine.ProcessEngineConfiguration;
import org.operaton.bpm.engine.impl.jobexecutor.JobExecutor;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

@ExtendWith({PostgresTestContainer.class})
public class KoraProcessEngineTests implements OperatonEngineBpmnModule {

    @Test
    void initTwoStage(PostgresParams params) {
        withDatabase(params, jdbc -> {
            var config = new $OperatonEngineBpmnConfig_ConfigValueExtractor.OperatonEngineBpmnConfig_Impl(
                new OperatonEngineBpmnConfig.ParallelInitConfig() {},
                $OperatonEngineBpmnConfig_JobExecutorConfig_ConfigValueExtractor.DEFAULTS,
                new $OperatonEngineBpmnConfig_DeploymentConfig_ConfigValueExtractor.DeploymentConfig_Impl(null, "MyDep", false, List.of("bpm"), null),
                new $OperatonEngineBpmnConfig_AdminConfig_ConfigValueExtractor.AdminConfig_Impl("admin", "admin", null, null, null),
                new $OperatonEngineBpmnConfig_OperatonTelemetryConfig_ConfigValueExtractor.OperatonTelemetryConfig_Impl(
                    new $OperatonEngineBpmnConfig_OperatonEngineLogConfig_ConfigValueExtractor.OperatonEngineLogConfig_Defaults(),
                    new $TelemetryConfig_TracingConfig_ConfigValueExtractor.TracingConfig_Impl(true, Map.of()),
                    true,
                    new $TelemetryConfig_MetricsConfig_ConfigValueExtractor.MetricsConfig_Impl(true, TelemetryConfig.MetricsConfig.DEFAULT_SLO, Map.of())
                )
            );

            KoraDelegateWrapperFactory koraDelegateWrapperFactory = koraJavaDelegateTelemetryWrapper(null, null);
            JobExecutor jobExecutor = operatonEngineBpmnKoraJobExecutor(config);

            OperatonEngineDataSource operatonEngineDataSource = new OperatonEngineDataSource() {

                @Override
                public OperatonTransactionManager transactionManager() {
                    return new JdbcOperatonTransactionManager(jdbc.value());
                }

                @Override
                public DataSource dataSource() {
                    return jdbc.value();
                }
            };

            ProcessEngineConfiguration koraProcessEngineConfiguration = operatonEngineBpmnKoraProcessEngineConfiguration(
                jobExecutor,
                operatonEngineBpmnIdGenerator(),
                operatonEngineBpmnKoraExpressionManager(operatonEngineBpmnKoraELResolver(koraDelegateWrapperFactory, All.of(), All.of())),
                operatonEngineBpmnKoraArtifactFactory(koraDelegateWrapperFactory, All.of(), All.of()),
                All.of(),
                operatonEngineDataSource,
                config,
                operatonEngineBpmnKoraComponentResolverFactory(koraDelegateWrapperFactory, All.of(), All.of())
            );

            var koraProcessEngine = operatonEngineBpmnKoraProcessEngine(koraProcessEngineConfiguration,
                config,
                All.of(
                    operatonEngineBpmnKoraProcessEngineTwoStageOperatonConfigurator(koraProcessEngineConfiguration, config, jobExecutor),
                    operatonEngineBpmnKoraAdminUserConfigurator(config, operatonEngineDataSource),
                    operatonEngineBpmnKoraResourceDeploymentConfigurator(config)
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
            var config = new $OperatonEngineBpmnConfig_ConfigValueExtractor.OperatonEngineBpmnConfig_Impl(
                new OperatonEngineBpmnConfig.ParallelInitConfig() {
                    @Override
                    public boolean enabled() {
                        return false;
                    }
                },
                $OperatonEngineBpmnConfig_JobExecutorConfig_ConfigValueExtractor.DEFAULTS,
                new $OperatonEngineBpmnConfig_DeploymentConfig_ConfigValueExtractor.DeploymentConfig_Impl(null, "MyDep", false, List.of("bpm"), null),
                new $OperatonEngineBpmnConfig_AdminConfig_ConfigValueExtractor.AdminConfig_Impl("admin", "admin", null, null, null),
                new $OperatonEngineBpmnConfig_OperatonTelemetryConfig_ConfigValueExtractor.OperatonTelemetryConfig_Impl(
                    new $OperatonEngineBpmnConfig_OperatonEngineLogConfig_ConfigValueExtractor.OperatonEngineLogConfig_Defaults(),
                    new $TelemetryConfig_TracingConfig_ConfigValueExtractor.TracingConfig_Impl(true, Map.of()),
                    true,
                    new $TelemetryConfig_MetricsConfig_ConfigValueExtractor.MetricsConfig_Impl(true, TelemetryConfig.MetricsConfig.DEFAULT_SLO, Map.of())
                )
            );

            KoraDelegateWrapperFactory koraDelegateWrapperFactory = koraJavaDelegateTelemetryWrapper(null, null);
            JobExecutor jobExecutor = operatonEngineBpmnKoraJobExecutor(config);

            OperatonEngineDataSource operatonEngineDataSource = new OperatonEngineDataSource() {

                @Override
                public OperatonTransactionManager transactionManager() {
                    return new JdbcOperatonTransactionManager(jdbc.value());
                }

                @Override
                public DataSource dataSource() {
                    return jdbc.value();
                }
            };

            ProcessEngineConfiguration koraProcessEngineConfiguration = operatonEngineBpmnKoraProcessEngineConfiguration(
                jobExecutor,
                operatonEngineBpmnIdGenerator(),
                operatonEngineBpmnKoraExpressionManager(operatonEngineBpmnKoraELResolver(koraDelegateWrapperFactory, All.of(), All.of())),
                operatonEngineBpmnKoraArtifactFactory(koraDelegateWrapperFactory, All.of(), All.of()),
                All.of(),
                operatonEngineDataSource,
                config,
                operatonEngineBpmnKoraComponentResolverFactory(koraDelegateWrapperFactory, All.of(), All.of())
            );

            KoraProcessEngine koraProcessEngine = operatonEngineBpmnKoraProcessEngine(koraProcessEngineConfiguration,
                config,
                All.of(
                    operatonEngineBpmnKoraAdminUserConfigurator(config, operatonEngineDataSource),
                    operatonEngineBpmnKoraResourceDeploymentConfigurator(config)
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

        var db = new JdbcDatabase(config, NoopDatabaseTelemetryFactory.INSTANCE, null);
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
