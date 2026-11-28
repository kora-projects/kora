package io.koraframework.camunda.engine.bpmn;

import io.koraframework.application.graph.*;
import io.koraframework.camunda.engine.bpmn.configurator.*;
import io.koraframework.camunda.engine.bpmn.telemetry.*;
import io.koraframework.camunda.engine.bpmn.telemetry.impl.*;
import io.koraframework.camunda.engine.bpmn.transaction.*;
import io.koraframework.common.annotation.*;
import io.koraframework.common.annotation.Tag;
import io.koraframework.common.context.*;
import io.koraframework.common.readiness.*;
import io.koraframework.common.telemetry.*;
import io.koraframework.config.common.*;
import io.koraframework.config.common.mapper.*;
import io.micrometer.core.instrument.*;
import io.opentelemetry.api.trace.*;
import org.camunda.bpm.engine.*;
import org.camunda.bpm.engine.delegate.*;
import org.camunda.bpm.engine.impl.cfg.*;
import org.camunda.bpm.engine.impl.el.*;
import org.camunda.bpm.engine.impl.jobexecutor.*;
import org.camunda.bpm.engine.impl.persistence.*;
import org.camunda.bpm.impl.juel.jakarta.el.*;
import org.jspecify.annotations.*;

import javax.sql.*;
import java.util.*;

/**
 * Use module: `io.koraframework:experimental.operaton.engine.bpmn` as a replacement for deprecated Camunda 7 engine
 * <a href="https://camunda.com/blog/2025/02/camunda-7-enterprise-end-of-life-extension/">Camunda 7 EOL</a>
 * <a href="https://operaton.org/">Operaton BPMN Engine</a>
 * <a href="https://github.com/operaton/migrate-from-camunda-recipe">OpenRewrite Migration Recipe</a>
 */
@Deprecated
public interface CamundaEngineBpmnModule {

    default CamundaEngineBpmnConfig camundaEngineBpmnConfig(Config config, ConfigValueMapper<CamundaEngineBpmnConfig> mapper) {
        return mapper.mapOrThrow(config.get("camunda.engine.bpmn"));
    }

    @Tag(CamundaBpmn.class)
    @DefaultComponent
    default DataSource camundaDataSource(DataSource dataSource) {
        return dataSource;
    }

    @DefaultComponent
    default CamundaEngineDataSource camundaKoraDataSource(@Tag(CamundaBpmn.class) DataSource dataSource) {
        return new CamundaEngineDataSource() {
            @Override
            public CamundaTransactionManager transactionManager() {
                return new JdbcCamundaTransactionManager(dataSource);
            }

            @Override
            public DataSource dataSource() {
                return dataSource;
            }
        };
    }

    @DefaultComponent
    default IdGenerator camundaEngineBpmnIdGenerator() {
        return new StrongUuidGenerator();
    }

    @DefaultComponent
    default JobExecutor camundaEngineBpmnKoraJobExecutor(CamundaEngineBpmnConfig engineConfig) {
        if (engineConfig.jobExecutor().virtualThreadsEnabled()) {
            return new KoraVirtualThreadJobExecutor(engineConfig);
        } else {
            return new KoraThreadPoolJobExecutor(engineConfig);
        }
    }

    default ReadinessProbe camundaEngineBpmnReadinessProbe(JobExecutor jobExecutor) {
        return new JobExecutorReadinessProbe(jobExecutor);
    }


    @DefaultComponent
    default CamundaEngineTelemetryFactory camundaEngineBpmnTelemetryFactory(@Nullable Tracer tracer,
                                                                            @Nullable MeterRegistry meterRegistry,
                                                                            @Nullable DefaultCamundaEngineLoggerFactory loggerFactory,
                                                                            @Nullable DefaultCamundaEngineMetricsFactory metricsFactory) {
        return new DefaultCamundaEngineTelemetryFactory(tracer, meterRegistry, loggerFactory, metricsFactory);
    }

    @DefaultComponent
    default KoraDelegateWrapperFactory koraJavaDelegateTelemetryWrapper(CamundaEngineTelemetryFactory telemetryFactory,
                                                                        CamundaEngineBpmnConfig camundaEngineBpmnConfig) {
        return delegate -> {
            var telemetry = telemetryFactory.get(camundaEngineBpmnConfig.telemetry());
            return execution -> {
                var observation = telemetry.observe(delegate.getClass().getCanonicalName());

                var current = KoraContext.current();
                var baseContext = current != null ? current : KoraContext.empty();

                var updatedContext = baseContext
                    .with(io.koraframework.logging.common.MDC.MDC_KEY, new io.koraframework.logging.common.MDC());

                Observation.attach(updatedContext, observation).call(() -> {
                    try {
                        observation.observeExecution(execution);
                        delegate.execute(execution);
                        return null;
                    } catch (Throwable e) {
                        observation.observeError(e);
                        throw e;
                    }
                });
            };
        };
    }

    @DefaultComponent
    default ELResolver camundaEngineBpmnKoraELResolver(KoraDelegateWrapperFactory wrapperFactory,
                                                       All<KoraDelegate> koraDelegates,
                                                       All<JavaDelegate> javaDelegates) {
        return new KoraELResolver(wrapperFactory, koraDelegates, javaDelegates);
    }

    @DefaultComponent
    default JuelExpressionManager camundaEngineBpmnKoraExpressionManager(ELResolver koraELResolver) {
        return new KoraExpressionManager(koraELResolver);
    }

    @DefaultComponent
    default ArtifactFactory camundaEngineBpmnKoraArtifactFactory(KoraDelegateWrapperFactory wrapperFactory,
                                                                 All<KoraDelegate> koraDelegates,
                                                                 All<JavaDelegate> javaDelegates) {
        return new KoraArtifactFactory(wrapperFactory, koraDelegates, javaDelegates);
    }

    @DefaultComponent
    default CamundaVersion camundaEngineBpmnPackageVersion() {
        return new CamundaVersion(Optional.ofNullable(ProcessEngine.class.getPackage().getImplementationVersion())
            .map(String::trim)
            .orElse(null));
    }

    @DefaultComponent
    default KoraResolverFactory camundaEngineBpmnKoraComponentResolverFactory(KoraDelegateWrapperFactory wrapperFactory,
                                                                              All<KoraDelegate> koraDelegates,
                                                                              All<JavaDelegate> javaDelegates) {
        return new KoraResolverFactory(wrapperFactory, koraDelegates, javaDelegates);
    }

    @DefaultComponent
    default ProcessEngineConfiguration camundaEngineBpmnKoraProcessEngineConfiguration(JobExecutor jobExecutor,
                                                                                       IdGenerator idGenerator,
                                                                                       JuelExpressionManager koraExpressionManager,
                                                                                       ArtifactFactory artifactFactory,
                                                                                       All<ProcessEnginePlugin> plugins,
                                                                                       CamundaEngineDataSource camundaEngineDataSource,
                                                                                       CamundaEngineBpmnConfig camundaEngineBpmnConfig,
                                                                                       KoraResolverFactory componentResolverFactory) {
        return new KoraProcessEngineConfiguration(jobExecutor, idGenerator, koraExpressionManager, artifactFactory, plugins, camundaEngineDataSource, camundaEngineBpmnConfig, componentResolverFactory);
    }

    @Root
    @DefaultComponent
    default KoraProcessEngine camundaEngineBpmnKoraProcessEngine(ProcessEngineConfiguration processEngineConfiguration,
                                                                 CamundaEngineBpmnConfig camundaEngineBpmnConfig,
                                                                 All<ProcessEngineConfigurator> camundaConfigurators) {
        return new KoraProcessEngine(processEngineConfiguration, camundaEngineBpmnConfig, camundaConfigurators);
    }

    default ProcessEngineConfigurator camundaEngineBpmnKoraAdminUserConfigurator(CamundaEngineBpmnConfig camundaEngineBpmnConfig, CamundaEngineDataSource camundaEngineDataSource) {
        return new AdminUserProcessEngineConfigurator(camundaEngineBpmnConfig, camundaEngineDataSource);
    }

    default ProcessEngineConfigurator camundaEngineBpmnKoraResourceDeploymentConfigurator(CamundaEngineBpmnConfig camundaEngineBpmnConfig) {
        return new DeploymentProcessEngineConfigurator(camundaEngineBpmnConfig);
    }

    default ProcessEngineConfigurator camundaEngineBpmnKoraProcessEngineTwoStageCamundaConfigurator(ProcessEngineConfiguration engineConfiguration,
                                                                                                    CamundaEngineBpmnConfig camundaEngineBpmnConfig,
                                                                                                    JobExecutor jobExecutor) {
        return new SecondStageKoraProcessEngineConfigurator(engineConfiguration, camundaEngineBpmnConfig, jobExecutor);
    }

    @Root
    default Lifecycle camundaKoraProcessEngineParallelInitializer(ProcessEngine processEngine,
                                                                  CamundaEngineBpmnConfig camundaEngineBpmnConfig,
                                                                  ProcessEngineConfiguration processEngineConfiguration,
                                                                  All<ProcessEngineConfigurator> camundaConfigurators) {
        return new KoraProcessEngineParallelInitializer(processEngine, camundaEngineBpmnConfig, processEngineConfiguration, camundaConfigurators);
    }

    default RuntimeService camundaEngineBpmnRuntimeService(ProcessEngine processEngine) {
        return processEngine.getRuntimeService();
    }

    default RepositoryService camundaEngineBpmnRepositoryService(ProcessEngine processEngine) {
        return processEngine.getRepositoryService();
    }

    default ManagementService camundaEngineBpmnManagementService(ProcessEngine processEngine) {
        return processEngine.getManagementService();
    }

    default AuthorizationService camundaEngineBpmnAuthorizationService(ProcessEngine processEngine) {
        return processEngine.getAuthorizationService();
    }

    default DecisionService camundaEngineBpmnDecisionService(ProcessEngine processEngine) {
        return processEngine.getDecisionService();
    }

    default ExternalTaskService camundaEngineBpmnExternalTaskService(ProcessEngine processEngine) {
        return processEngine.getExternalTaskService();
    }

    default FilterService camundaEngineBpmnFilterService(ProcessEngine processEngine) {
        return processEngine.getFilterService();
    }

    default FormService camundaEngineBpmnFormService(ProcessEngine processEngine) {
        return processEngine.getFormService();
    }

    default TaskService camundaEngineBpmnTaskService(ProcessEngine processEngine) {
        return processEngine.getTaskService();
    }

    default HistoryService camundaEngineBpmnHistoryService(ProcessEngine processEngine) {
        return processEngine.getHistoryService();
    }

    default IdentityService camundaEngineBpmnIdentityService(ProcessEngine processEngine) {
        return processEngine.getIdentityService();
    }
}
