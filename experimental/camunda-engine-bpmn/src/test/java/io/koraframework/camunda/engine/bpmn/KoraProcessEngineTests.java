package io.koraframework.camunda.engine.bpmn;

import io.koraframework.application.graph.All;
import io.koraframework.camunda.engine.bpmn.telemetry.*;
import io.koraframework.camunda.engine.bpmn.telemetry.$CamundaEngineTelemetryConfig_CamundaEngineLoggingConfig_ConfigValueMapper;
import io.koraframework.camunda.engine.bpmn.telemetry.$CamundaEngineTelemetryConfig_ConfigValueMapper;
import io.koraframework.camunda.engine.bpmn.transaction.CamundaTransactionManager;
import io.koraframework.camunda.engine.bpmn.transaction.JdbcCamundaTransactionManager;
import io.koraframework.database.common.telemetry.*;
import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_DatabaseLoggingConfig_ConfigValueMapper;
import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_DatabaseTracingConfig_ConfigValueMapper;
import io.koraframework.database.common.telemetry.impl.NoopDatabaseTelemetryFactory;
import io.koraframework.database.jdbc.$JdbcDatabaseConfig_ConfigValueMapper;
import io.koraframework.database.jdbc.JdbcDatabase;
import io.koraframework.telemetry.common.TelemetryConfig;
import io.koraframework.test.postgres.PostgresParams;
import io.koraframework.test.postgres.PostgresTestContainer;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.jobexecutor.JobExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

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
            var config = new $CamundaEngineBpmnConfig_ConfigValueMapper.CamundaEngineBpmnConfig_Impl(
                new CamundaEngineBpmnConfig.ParallelInitConfig() {},
                $CamundaEngineBpmnConfig_JobExecutorConfig_ConfigValueMapper.DEFAULTS,
                new $CamundaEngineBpmnConfig_DeploymentConfig_ConfigValueMapper.DeploymentConfig_Impl(null, "MyDep", false, List.of("bpm"), null),
                new $CamundaEngineBpmnConfig_AdminConfig_ConfigValueMapper.AdminConfig_Impl("admin", "admin", null, null, null),
                new $CamundaEngineTelemetryConfig_ConfigValueMapper.CamundaEngineTelemetryConfig_Impl(
                    new $CamundaEngineTelemetryConfig_CamundaEngineLoggingConfig_ConfigValueMapper.CamundaEngineLoggingConfig_Defaults(),
                    new $CamundaEngineTelemetryConfig_CamundaEngineMetricsConfig_ConfigValueMapper.CamundaEngineMetricsConfig_Defaults(),
                    new $CamundaEngineTelemetryConfig_CamundaEngineTracingConfig_ConfigValueMapper.CamundaEngineTracingConfig_Defaults()
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
                camundaEngineBpmnIdGenerator(),
                camundaEngineBpmnKoraExpressionManager(camundaEngineBpmnKoraELResolver(koraDelegateWrapperFactory, All.of(), All.of())),
                camundaEngineBpmnKoraArtifactFactory(koraDelegateWrapperFactory, All.of(), All.of()),
                All.of(),
                camundaEngineDataSource,
                config,
                camundaEngineBpmnKoraComponentResolverFactory(koraDelegateWrapperFactory, All.of(), All.of())
            );

            var koraProcessEngine = camundaEngineBpmnKoraProcessEngine(koraProcessEngineConfiguration,
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
            var config = new $CamundaEngineBpmnConfig_ConfigValueMapper.CamundaEngineBpmnConfig_Impl(
                new CamundaEngineBpmnConfig.ParallelInitConfig() {
                    @Override
                    public boolean enabled() {
                        return false;
                    }
                },
                $CamundaEngineBpmnConfig_JobExecutorConfig_ConfigValueMapper.DEFAULTS,
                new $CamundaEngineBpmnConfig_DeploymentConfig_ConfigValueMapper.DeploymentConfig_Impl(null, "MyDep", false, List.of("bpm"), null),
                new $CamundaEngineBpmnConfig_AdminConfig_ConfigValueMapper.AdminConfig_Impl("admin", "admin", null, null, null),
                new $CamundaEngineTelemetryConfig_ConfigValueMapper.CamundaEngineTelemetryConfig_Impl(
                    new $CamundaEngineTelemetryConfig_CamundaEngineLoggingConfig_ConfigValueMapper.CamundaEngineLoggingConfig_Defaults(),
                    new $CamundaEngineTelemetryConfig_CamundaEngineMetricsConfig_ConfigValueMapper.CamundaEngineMetricsConfig_Defaults(),
                    new $CamundaEngineTelemetryConfig_CamundaEngineTracingConfig_ConfigValueMapper.CamundaEngineTracingConfig_Defaults()
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
                camundaEngineBpmnIdGenerator(),
                camundaEngineBpmnKoraExpressionManager(camundaEngineBpmnKoraELResolver(koraDelegateWrapperFactory, All.of(), All.of())),
                camundaEngineBpmnKoraArtifactFactory(koraDelegateWrapperFactory, All.of(), All.of()),
                All.of(),
                camundaEngineDataSource,
                config,
                camundaEngineBpmnKoraComponentResolverFactory(koraDelegateWrapperFactory, All.of(), All.of())
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
        var config = new $JdbcDatabaseConfig_ConfigValueMapper.JdbcDatabaseConfig_Impl(
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
            new $DatabaseTelemetryConfig_ConfigValueMapper.DatabaseTelemetryConfig_Impl(
                new $DatabaseTelemetryConfig_DatabaseLoggingConfig_ConfigValueMapper.DatabaseLoggingConfig_Impl(true),
                new $DatabaseTelemetryConfig_DatabaseMetricsConfig_ConfigValueMapper.DatabaseMetricsConfig_Impl(true, true, TelemetryConfig.MetricsConfig.DEFAULT_SLO, Map.of()),
                new $DatabaseTelemetryConfig_DatabaseTracingConfig_ConfigValueMapper.DatabaseTracingConfig_Impl(true, Map.of())
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
