package ru.tinkoff.kora.bpmn.camunda7.engine;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.util.List;

@ConfigValueExtractor
public interface Camunda7EngineConfig {

    default boolean initializeParallel() {
        return true;
    }

    @Nullable
    String licensePath();

    JobExecutorConfig jobExecutor();

    MetricsConfig metrics();

    TelemetryConfig telemetry();

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
            return "KoraAutoDeployment";
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
    interface MetricsConfig {

        default boolean metricsEnabled() {
            return true;
        }

        default boolean taskMetricsEnabled() {
            return true;
        }
    }

    @ConfigValueExtractor
    interface TelemetryConfig {

        default boolean telemetryEnabled() {
            return true;
        }

        default boolean telemetryReporterEnabled() {
            return true;
        }
    }
}
