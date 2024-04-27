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
                $Camunda7EngineConfig_JobExecutorConfig_ConfigValueExtractor.DEFAULTS,
                $Camunda7EngineConfig_MetricsConfig_ConfigValueExtractor.DEFAULTS,
                $Camunda7EngineConfig_TelemetryConfig_ConfigValueExtractor.DEFAULTS,
                new $Camunda7EngineConfig_FilterConfig_ConfigValueExtractor.FilterConfig_Impl("All tasks"),
                new $Camunda7EngineConfig_DeploymentConfig_ConfigValueExtractor.DeploymentConfig_Impl(null, "MyDep", false, List.of("bpm")),
                new $Camunda7EngineConfig_AdminConfig_ConfigValueExtractor.AdminConfig_Impl("admin", "admin", null, null, null)
            );

            JobExecutor jobExecutor = camunda7KoraJobExecutor(config);
            KoraProcessEngineConfiguration koraProcessEngineConfiguration = camunda7KoraProcessEngineConfiguration(
                jobExecutor,
                camunda7KoraTelemetryRegistry(null),
                camunda7IdGenerator(),
                camunda7KoraExpressionManager(camunda7KoraELResolver(All.of(), All.of())),
                camunda7KoraArtifactFactory(All.of()),
                All.of(),
                jdbc,
                jdbc.value(),
                config,
                camunda7KoraComponentResolverFactory(All.of(), All.of()),
                camunda7PackageVersion()
            );

            KoraProcessEngine koraProcessEngine = camunda7KoraProcessEngine(koraProcessEngineConfiguration);
            try {
                koraProcessEngine.init();

                KoraProcessEngineConfigurator trigger = camunda7KoraProcessEngineConfigurator(koraProcessEngine.value(), All.of(
                    camunda7KoraProcessEngineTwoStageCamundaConfigurator(koraProcessEngineConfiguration, config, jobExecutor),
                    camunda7KoraAdminUserConfigurator(config, jdbc),
                    camunda7KoraLicenseKeyConfigurator(config, camunda7PackageVersion()),
                    camunda7KoraFilterAllTaskConfigurator(config),
                    camunda7KoraResourceDeploymentConfigurator(config)
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
                $Camunda7EngineConfig_JobExecutorConfig_ConfigValueExtractor.DEFAULTS,
                $Camunda7EngineConfig_MetricsConfig_ConfigValueExtractor.DEFAULTS,
                $Camunda7EngineConfig_TelemetryConfig_ConfigValueExtractor.DEFAULTS,
                new $Camunda7EngineConfig_FilterConfig_ConfigValueExtractor.FilterConfig_Impl("All tasks"),
                new $Camunda7EngineConfig_DeploymentConfig_ConfigValueExtractor.DeploymentConfig_Impl(null, "MyDep", false, List.of("bpm")),
                new $Camunda7EngineConfig_AdminConfig_ConfigValueExtractor.AdminConfig_Impl("admin", "admin", null, null, null)
            );

            JobExecutor jobExecutor = camunda7KoraJobExecutor(config);
            KoraProcessEngineConfiguration koraProcessEngineConfiguration = camunda7KoraProcessEngineConfiguration(
                jobExecutor,
                camunda7KoraTelemetryRegistry(null),
                camunda7IdGenerator(),
                camunda7KoraExpressionManager(camunda7KoraELResolver(All.of(), All.of())),
                camunda7KoraArtifactFactory(All.of()),
                All.of(),
                jdbc,
                jdbc.value(),
                config,
                camunda7KoraComponentResolverFactory(All.of(), All.of()),
                camunda7PackageVersion()
            );

            KoraProcessEngine koraProcessEngine = camunda7KoraProcessEngine(koraProcessEngineConfiguration);
            try {
                koraProcessEngine.init();

                KoraProcessEngineConfigurator trigger = camunda7KoraProcessEngineConfigurator(koraProcessEngine.value(), All.of(
                    camunda7KoraAdminUserConfigurator(config, jdbc),
                    camunda7KoraLicenseKeyConfigurator(config, camunda7PackageVersion()),
                    camunda7KoraFilterAllTaskConfigurator(config),
                    camunda7KoraResourceDeploymentConfigurator(config)
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
