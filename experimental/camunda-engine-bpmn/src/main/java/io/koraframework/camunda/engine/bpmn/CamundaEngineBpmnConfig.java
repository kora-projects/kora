package io.koraframework.camunda.engine.bpmn;

import io.koraframework.camunda.engine.bpmn.telemetry.CamundaEngineTelemetryConfig;
import io.koraframework.config.common.annotation.ConfigMapper;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;

@ConfigMapper
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
    CamundaEngineTelemetryConfig telemetry();

    @ConfigMapper
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

    @ConfigMapper
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

    @ConfigMapper
    interface FilterConfig {

        /**
         * @return Name of the Camunda filter to create.
         */
        String create();
    }

    @ConfigMapper
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

    @ConfigMapper
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
}
