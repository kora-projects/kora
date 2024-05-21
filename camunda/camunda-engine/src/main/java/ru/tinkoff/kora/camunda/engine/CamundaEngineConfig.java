package ru.tinkoff.kora.camunda.engine;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.List;

@ConfigValueExtractor
public interface CamundaEngineConfig {

    default boolean initializeParallel() {
        return true;
    }

    @Nullable
    String licensePath();

    JobExecutorConfig jobExecutor();

    CamundaTelemetryConfig telemetry();

    @Nullable
    FilterConfig filter();

    @Nullable
    DeploymentConfig deployment();

    @Nullable
    AdminConfig admin();

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
            return 100;
        }

        default Integer queueSize() {
            return 25;
        }

        default Integer maxJobsPerAcquisition() {
            return 5;
        }
    }

    @ConfigValueExtractor
    interface CamundaTelemetryConfig extends TelemetryConfig {

        @Override
        CamundaEngineLogConfig logging();

        default boolean engineTelemetryEnabled() {
            return true;
        }

        default boolean engineTelemetryReporterEnabled() {
            return true;
        }
    }

    @ConfigValueExtractor
    interface CamundaEngineLogConfig extends TelemetryConfig.LogConfig {

        default boolean stacktrace() {
            return true;
        }
    }
}
