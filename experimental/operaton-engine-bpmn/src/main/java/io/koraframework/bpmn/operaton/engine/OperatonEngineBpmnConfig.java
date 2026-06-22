package io.koraframework.bpmn.operaton.engine;

import io.koraframework.bpmn.operaton.engine.telemetry.OperatonEngineTelemetryConfig;
import io.koraframework.config.common.annotation.ConfigValueExtractor;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;

@ConfigValueExtractor
public interface OperatonEngineBpmnConfig {

    ParallelInitConfig parallelInitialization();

    JobExecutorConfig jobExecutor();

    @Nullable
    DeploymentConfig deployment();

    @Nullable
    AdminConfig admin();

    OperatonEngineTelemetryConfig telemetry();

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
}
