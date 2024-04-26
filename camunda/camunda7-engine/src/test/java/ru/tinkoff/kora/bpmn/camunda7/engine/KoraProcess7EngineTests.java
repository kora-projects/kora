package ru.tinkoff.kora.bpmn.camunda7.engine;

import org.camunda.bpm.engine.impl.jobexecutor.JobExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.database.common.telemetry.DefaultDataBaseTelemetryFactory;
import ru.tinkoff.kora.database.jdbc.$JdbcDatabaseConfig_ConfigValueExtractor;
import ru.tinkoff.kora.database.jdbc.JdbcDatabase;
import ru.tinkoff.kora.telemetry.common.$TelemetryConfig_ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.$TelemetryConfig_LogConfig_ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.$TelemetryConfig_MetricsConfig_ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.$TelemetryConfig_TracingConfig_ConfigValueExtractor;
import ru.tinkoff.kora.test.postgres.PostgresParams;
import ru.tinkoff.kora.test.postgres.PostgresTestContainer;

import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

@ExtendWith({PostgresTestContainer.class})
public class KoraProcess7EngineTests implements Camunda7EngineModule {

    @Test
    void initTwoStage(PostgresParams params) {
        withDatabase(params, jdbc -> {
            var config = new $Camunda7EngineConfig_ConfigValueExtractor.Camunda7EngineConfig_Impl(
                true,
                "camunda-license.txt",
                $Camunda7EngineConfig_CamundaJobExecutorConfig_ConfigValueExtractor.DEFAULTS,
                $Camunda7EngineConfig_CamundaMetricsConfig_ConfigValueExtractor.DEFAULTS,
                $Camunda7EngineConfig_CamundaTelemetryConfig_ConfigValueExtractor.DEFAULTS,
                new $Camunda7EngineConfig_CamundaFilterConfig_ConfigValueExtractor.FilterConfig_Impl("All tasks"),
                new $Camunda7EngineConfig_CamundaDeploymentConfig_ConfigValueExtractor.DeploymentConfig_Impl(null, "MyDep", false, List.of("bpm")),
                new $Camunda7EngineConfig_CamundaAdminUser_ConfigValueExtractor.AdminConfig_Impl("admin", "admin", null, null, null)
            );

            JobExecutor jobExecutor = camundaKoraJobExecutor(config);
            KoraProcessEngineConfiguration koraProcessEngineConfiguration = camundaKoraProcessEngineConfiguration(
                jobExecutor,
                camundaKoraTelemetryRegistry(null),
                camundaIdGenerator(),
                camundaKoraExpressionManager(camundaKoraELResolver(All.of(), All.of())),
                camundaKoraArtifactFactory(All.of()),
                All.of(),
                jdbc,
                jdbc.value(),
                config,
                camundaKoraComponentResolverFactory(All.of(), All.of()),
                camundaPackageVersion()
            );

            KoraProcessEngine koraProcessEngine = camundaKoraProcessEngine(koraProcessEngineConfiguration);
            try {
                koraProcessEngine.init();

                KoraProcessEngineConfigurator trigger = camundaKoraProcessEngineConfigurator(koraProcessEngine.value(), All.of(
                    camundaKoraProcessEngineTwoStageCamundaConfigurator(koraProcessEngineConfiguration, config, jobExecutor),
                    camundaKoraAdminUserConfigurator(config, jdbc),
                    camundaKoraLicenseKeyConfigurator(config, camundaPackageVersion()),
                    camundaKoraFilterAllTaskConfigurator(config),
                    camundaKoraResourceDeploymentConfigurator(config)
                ));

                trigger.init();
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
            var config = new $Camunda7EngineConfig_ConfigValueExtractor.Camunda7EngineConfig_Impl(
                false,
                "camunda-license.txt",
                $Camunda7EngineConfig_CamundaJobExecutorConfig_ConfigValueExtractor.DEFAULTS,
                $Camunda7EngineConfig_CamundaMetricsConfig_ConfigValueExtractor.DEFAULTS,
                $Camunda7EngineConfig_CamundaTelemetryConfig_ConfigValueExtractor.DEFAULTS,
                new $Camunda7EngineConfig_CamundaFilterConfig_ConfigValueExtractor.FilterConfig_Impl("All tasks"),
                new $Camunda7EngineConfig_CamundaDeploymentConfig_ConfigValueExtractor.DeploymentConfig_Impl(null, "MyDep", false, List.of("bpm")),
                new $Camunda7EngineConfig_CamundaAdminUser_ConfigValueExtractor.AdminConfig_Impl("admin", "admin", null, null, null)
            );

            JobExecutor jobExecutor = camundaKoraJobExecutor(config);
            KoraProcessEngineConfiguration koraProcessEngineConfiguration = camundaKoraProcessEngineConfiguration(
                jobExecutor,
                camundaKoraTelemetryRegistry(null),
                camundaIdGenerator(),
                camundaKoraExpressionManager(camundaKoraELResolver(All.of(), All.of())),
                camundaKoraArtifactFactory(All.of()),
                All.of(),
                jdbc,
                jdbc.value(),
                config,
                camundaKoraComponentResolverFactory(All.of(), All.of()),
                camundaPackageVersion()
            );

            KoraProcessEngine koraProcessEngine = camundaKoraProcessEngine(koraProcessEngineConfiguration);
            try {
                koraProcessEngine.init();

                KoraProcessEngineConfigurator trigger = camundaKoraProcessEngineConfigurator(koraProcessEngine.value(), All.of(
                    camundaKoraAdminUserConfigurator(config, jdbc),
                    camundaKoraLicenseKeyConfigurator(config, camundaPackageVersion()),
                    camundaKoraFilterAllTaskConfigurator(config),
                    camundaKoraResourceDeploymentConfigurator(config)
                ));

                trigger.init();
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
                new $TelemetryConfig_MetricsConfig_ConfigValueExtractor.MetricsConfig_Defaults()
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
