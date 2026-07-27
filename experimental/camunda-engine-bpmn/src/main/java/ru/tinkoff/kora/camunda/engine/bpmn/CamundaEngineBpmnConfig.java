package ru.tinkoff.kora.camunda.engine.bpmn;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.time.Duration;
import java.util.List;

@ConfigValueExtractor
public interface CamundaEngineBpmnConfig {

    /**
     * @return Parallel engine initialization configuration.
     */
    ParallelInitConfig parallelInitialization();

    /**
     * @return JobExecutor configuration.
     */
    JobExecutorConfig jobExecutor();

    /**
     * @return Automatic BPMN / FORM / DMN resource deployment configuration.
     */
    @Nullable
    DeploymentConfig deployment();

    /**
     * @return Camunda administrator user configuration.
     */
    @Nullable
    AdminConfig admin();

    /**
     * @return Telemetry configuration of the module.
     */
    CamundaTelemetryConfig telemetry();

    @ConfigValueExtractor
    interface ParallelInitConfig {

        /**
         * @return Whether parallel engine initialization is enabled.
         */
        default boolean enabled() {
            return true;
        }

        /**
         * @return Whether incomplete engine statements are validated during parallel initialization.
         */
        default boolean validateIncompleteStatements() {
            return true;
        }
    }

    @ConfigValueExtractor
    interface AdminConfig {

        /**
         * @return Camunda administrator identifier.
         */
        String id();

        /**
         * @return Camunda administrator password.
         */
        String password();

        /**
         * @return Camunda administrator first name, uppercase identifier is used when not specified.
         */
        @Nullable
        String firstname();

        /**
         * @return Camunda administrator last name, uppercase identifier is used when not specified.
         */
        @Nullable
        String lastname();

        /**
         * @return Camunda administrator email address.
         */
        @Nullable
        String email();
    }

    @ConfigValueExtractor
    interface FilterConfig {

        /**
         * @return Name of the Camunda filter to create.
         */
        String create();
    }

    @ConfigValueExtractor
    interface DeploymentConfig {

        /**
         * @return Tenant identifier the deployment is bound to.
         */
        @Nullable
        String tenantId();

        /**
         * @return Resource deployment name.
         */
        default String name() {
            return "KoraEngineAutoDeployment";
        }

        /**
         * @return Whether only changed resources are deployed through Camunda duplicate filtering.
         */
        default boolean deployChangedOnly() {
            return true;
        }

        /**
         * @return Paths where BPMN / FORM / DMN resources are searched for, only the classpath: prefix is supported.
         */
        List<String> resources();

        /**
         * @return Delay before deploying resources to the engine.
         */
        @Nullable
        Duration delay();
    }

    @ConfigValueExtractor
    interface JobExecutorConfig {

        /**
         * @return Minimum number of permanently alive threads in the JobExecutor.
         */
        default Integer corePoolSize() {
            return 5;
        }

        /**
         * @return Maximum number of threads in the JobExecutor.
         */
        default Integer maxPoolSize() {
            return 25;
        }

        /**
         * @return JobExecutor task queue size before new tasks are rejected.
         */
        default Integer queueSize() {
            return 25;
        }

        /**
         * @return Maximum number of jobs acquired by the JobExecutor in one request.
         */
        default Integer maxJobsPerAcquisition() {
            return Runtime.getRuntime().availableProcessors() * 2;
        }

        /**
         * @return Whether virtual threads are used as the JobExecutor base, making pool and queue size settings unused.
         */
        default boolean virtualThreadsEnabled() {
            return false;
        }
    }

    @ConfigValueExtractor
    interface CamundaTelemetryConfig extends TelemetryConfig {

        /**
         * @return Logging telemetry configuration.
         */
        @Override
        CamundaEngineLogConfig logging();

        /**
         * @return Tracing telemetry configuration.
         */
        @Override
        TracingConfig tracing();

        /**
         * @return Whether the built-in Camunda engine telemetry collection is enabled.
         */
        default boolean engineTelemetryEnabled() {
            return false;
        }
    }

    @ConfigValueExtractor
    interface CamundaEngineLogConfig extends TelemetryConfig.LogConfig {

        /**
         * @return Whether error stack traces are logged.
         */
        default boolean stacktrace() {
            return true;
        }
    }

    @ConfigValueExtractor
    interface CamundaEngineTelemetryConfig extends TelemetryConfig.LogConfig {

        /**
         * @return Whether error stack traces are logged.
         */
        default boolean stacktrace() {
            return true;
        }
    }
}
