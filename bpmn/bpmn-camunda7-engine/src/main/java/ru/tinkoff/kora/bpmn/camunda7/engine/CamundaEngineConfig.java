package ru.tinkoff.kora.bpmn.camunda7.engine;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.util.List;

@ConfigValueExtractor
public interface CamundaEngineConfig {

    default boolean twoStage() {
        return true;
    }

    default String licensePath() {
        return "camunda-license.txt";
    }

    CamundaJobExecutorConfig jobExecutor();

    CamundaMetricsConfig metrics();

    CamundaTelemetryConfig telemetry();

    @Nullable
    CamundaFilterConfig filter();

    @Nullable
    CamundaDeploymentConfig deployment();

    @Nullable
    CamundaAdminUser admin();

    @ConfigValueExtractor
    interface CamundaAdminUser {

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
    interface CamundaFilterConfig {

        String create();
    }

    @ConfigValueExtractor
    interface CamundaDeploymentConfig {

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
    interface CamundaJobExecutorConfig {

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
    interface CamundaMetricsConfig {

        default boolean metricsEnabled() {
            return true;
        }

        default boolean taskMetricsEnabled() {
            return true;
        }
    }

    @ConfigValueExtractor
    interface CamundaTelemetryConfig {

        default boolean telemetryEnabled() {
            return true;
        }

        default boolean telemetryReporterEnabled() {
            return true;
        }
    }
}
