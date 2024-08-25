package ru.tinkoff.kora.camunda.engine;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.List;

@ConfigValueExtractor
public interface CamundaEngineConfig {

    ParallelInitConfig parallelInitialization();

    @Nullable
    String licensePath();

    JobExecutorConfig jobExecutor();

    @Nullable
    DeploymentConfig deployment();

    @Nullable
    AdminConfig admin();

    CamundaTelemetryConfig telemetry();

    @ConfigValueExtractor
    interface ParallelInitConfig {

        default boolean enabled() {
            return true;
        }

        default boolean validateIncompleteStatements() {
            return true;
        }
    }

    @ConfigValueExtractor
    interface AdminConfig {

        String id();

        String password();

        @Nullable
        String firstname();

        @Nullable
        String lastname();

        @Nullable
        String email();
    }

    @ConfigValueExtractor
    interface FilterConfig {

        String create();
    }

    @ConfigValueExtractor
    interface DeploymentConfig {

        @Nullable
        String tenantId();

        default String name() {
            return "KoraEngineAutoDeployment";
        }

        default boolean deployChangedOnly() {
            return true;
        }

        List<String> resources();
    }

    @ConfigValueExtractor
    interface JobExecutorConfig {

        default Integer corePoolSize() {
            return 5;
        }

        default Integer maxPoolSize() {
            return 25;
        }

        default Integer queueSize() {
            return 25;
        }

        default Integer maxJobsPerAcquisition() {
            return Runtime.getRuntime().availableProcessors() * 2;
        }
    }

    @ConfigValueExtractor
    interface CamundaTelemetryConfig extends TelemetryConfig {

        @Override
        CamundaEngineLogConfig logging();

        @Override
        TracingConfig tracing();

        default boolean engineTelemetryEnabled() {
            return false;
        }
    }

    @ConfigValueExtractor
    interface CamundaEngineLogConfig extends TelemetryConfig.LogConfig {

        default boolean stacktrace() {
            return true;
        }
    }

    @ConfigValueExtractor
    interface CamundaEngineTelemetryConfig extends TelemetryConfig.LogConfig {

        default boolean stacktrace() {
            return true;
        }
    }
}
