package io.koraframework.camunda.engine.bpmn;

import io.koraframework.camunda.engine.bpmn.telemetry.CamundaEngineTelemetryConfig;
import io.koraframework.config.common.annotation.ConfigMapper;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;

@ConfigMapper
public interface CamundaEngineBpmnConfig {

    ParallelInitConfig parallelInitialization();

    JobExecutorConfig jobExecutor();

    @Nullable
    DeploymentConfig deployment();

    @Nullable
    AdminConfig admin();

    CamundaEngineTelemetryConfig telemetry();

    @ConfigMapper
    interface ParallelInitConfig {

        default boolean enabled() {
            return true;
        }

        default boolean validateIncompleteStatements() {
            return true;
        }
    }

    @ConfigMapper
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

    @ConfigMapper
    interface FilterConfig {

        String create();
    }

    @ConfigMapper
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

    @ConfigMapper
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
}
