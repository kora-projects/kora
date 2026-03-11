package io.koraframework.camunda.engine.bpmn;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.telemetry.common.TelemetryConfig;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;

@ConfigValueExtractor
public interface CamundaEngineBpmnConfig {

    ParallelInitConfig parallelInitialization();

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

        @Nullable
        Duration delay();
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

        default boolean virtualThreadsEnabled() {
            return false;
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
