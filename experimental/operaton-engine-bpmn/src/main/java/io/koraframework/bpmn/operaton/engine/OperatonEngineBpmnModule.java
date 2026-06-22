package io.koraframework.bpmn.operaton.engine;

import io.koraframework.application.graph.All;
import io.koraframework.application.graph.Lifecycle;
import io.koraframework.common.DefaultComponent;
import io.koraframework.common.Tag;
import io.koraframework.common.annotation.Root;
import io.koraframework.common.readiness.ReadinessProbe;
import io.koraframework.common.telemetry.Observation;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractor;
import io.koraframework.logging.common.MDC;
import io.koraframework.bpmn.operaton.engine.configurator.AdminUserProcessEngineConfigurator;
import io.koraframework.bpmn.operaton.engine.configurator.DeploymentProcessEngineConfigurator;
import io.koraframework.bpmn.operaton.engine.configurator.ProcessEngineConfigurator;
import io.koraframework.bpmn.operaton.engine.configurator.SecondStageKoraProcessEngineConfigurator;
import io.koraframework.bpmn.operaton.engine.telemetry.OperatonEngineTelemetryFactory;
import io.koraframework.bpmn.operaton.engine.telemetry.impl.DefaultOperatonEngineLoggerFactory;
import io.koraframework.bpmn.operaton.engine.telemetry.impl.DefaultOperatonEngineMetricsFactory;
import io.koraframework.bpmn.operaton.engine.telemetry.impl.DefaultOperatonEngineTelemetryFactory;
import io.koraframework.bpmn.operaton.engine.transaction.OperatonTransactionManager;
import io.koraframework.bpmn.operaton.engine.transaction.JdbcOperatonTransactionManager;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import jakarta.el.ELResolver;
import org.jspecify.annotations.Nullable;
import org.operaton.bpm.engine.*;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.operaton.bpm.engine.impl.cfg.IdGenerator;
import org.operaton.bpm.engine.impl.cfg.ProcessEnginePlugin;
import org.operaton.bpm.engine.impl.el.JuelExpressionManager;
import org.operaton.bpm.engine.impl.jobexecutor.JobExecutor;
import org.operaton.bpm.engine.impl.persistence.StrongUuidGenerator;

import javax.sql.DataSource;

public interface OperatonEngineBpmnModule {

    default OperatonEngineBpmnConfig operatonEngineBpmnConfig(Config config, ConfigValueExtractor<OperatonEngineBpmnConfig> extractor) {
        return extractor.extract(config.get("operaton.engine.bpmn"));
    }

    @Tag(OperatonEngine.class)
    @DefaultComponent
    default DataSource operatonDataSource(DataSource dataSource) {
        return dataSource;
    }

    @DefaultComponent
    default OperatonEngineDataSource operatonKoraDataSource(@Tag(OperatonEngine.class) DataSource dataSource) {
        return new OperatonEngineDataSource() {
            @Override
            public OperatonTransactionManager transactionManager() {
                return new JdbcOperatonTransactionManager(dataSource);
            }

            @Override
            public DataSource dataSource() {
                return dataSource;
            }
        };
    }

    @DefaultComponent
    default IdGenerator operatonEngineBpmnIdGenerator() {
        return new StrongUuidGenerator();
    }

    @DefaultComponent
    default JobExecutor operatonEngineBpmnKoraJobExecutor(OperatonEngineBpmnConfig engineConfig) {
        if (engineConfig.jobExecutor().virtualThreadsEnabled()) {
            return new KoraVirtualThreadJobExecutor(engineConfig);
        } else {
            return new KoraThreadPoolJobExecutor(engineConfig);
        }
    }

    default ReadinessProbe operatonEngineBpmnReadinessProbe(JobExecutor jobExecutor) {
        return new JobExecutorReadinessProbe(jobExecutor);
    }


    @DefaultComponent
    default OperatonEngineTelemetryFactory operatonEngineBpmnTelemetryFactory(@Nullable Tracer tracer,
                                                                              @Nullable MeterRegistry meterRegistry,
                                                                              @Nullable DefaultOperatonEngineLoggerFactory loggerFactory,
                                                                              @Nullable DefaultOperatonEngineMetricsFactory metricsFactory) {
        return new DefaultOperatonEngineTelemetryFactory(tracer, meterRegistry, loggerFactory, metricsFactory);
    }

    @DefaultComponent
    default KoraDelegateWrapperFactory koraJavaDelegateTelemetryWrapper(OperatonEngineTelemetryFactory telemetryFactory,
                                                                        OperatonEngineBpmnConfig operatonEngineBpmnConfig) {
        return delegate -> {
            var telemetry = telemetryFactory.get(operatonEngineBpmnConfig.telemetry());
            return execution -> {
                var observation = telemetry.observe(delegate.getClass().getCanonicalName());
                Observation.scoped(observation)
                    .where(MDC.VALUE, new MDC())
                    .call(() -> {
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
    default ELResolver operatonEngineBpmnKoraELResolver(KoraDelegateWrapperFactory wrapperFactory,
                                                       All<KoraDelegate> koraDelegates,
                                                       All<JavaDelegate> javaDelegates) {
        return new KoraELResolver(wrapperFactory, koraDelegates, javaDelegates);
    }

    @DefaultComponent
    default JuelExpressionManager operatonEngineBpmnKoraExpressionManager(ELResolver koraELResolver) {
        return new KoraExpressionManager(koraELResolver);
    }

    @DefaultComponent
    default ArtifactFactory operatonEngineBpmnKoraArtifactFactory(KoraDelegateWrapperFactory wrapperFactory,
                                                                 All<KoraDelegate> koraDelegates,
                                                                 All<JavaDelegate> javaDelegates) {
        return new KoraArtifactFactory(wrapperFactory, koraDelegates, javaDelegates);
    }

    @DefaultComponent
    default KoraResolverFactory operatonEngineBpmnKoraComponentResolverFactory(KoraDelegateWrapperFactory wrapperFactory,
                                                                              All<KoraDelegate> koraDelegates,
                                                                              All<JavaDelegate> javaDelegates) {
        return new KoraResolverFactory(wrapperFactory, koraDelegates, javaDelegates);
    }

    @DefaultComponent
    default ProcessEngineConfiguration operatonEngineBpmnKoraProcessEngineConfiguration(JobExecutor jobExecutor,
                                                                                       IdGenerator idGenerator,
                                                                                       JuelExpressionManager koraExpressionManager,
                                                                                       ArtifactFactory artifactFactory,
                                                                                       All<ProcessEnginePlugin> plugins,
                                                                                       OperatonEngineDataSource engineDataSource,
                                                                                       OperatonEngineBpmnConfig operatonEngineBpmnConfig,
                                                                                       KoraResolverFactory componentResolverFactory) {
        return new KoraProcessEngineConfiguration(jobExecutor, idGenerator, koraExpressionManager, artifactFactory, plugins, engineDataSource, operatonEngineBpmnConfig, componentResolverFactory);
    }

    @Root
    @DefaultComponent
    default KoraProcessEngine operatonEngineBpmnKoraProcessEngine(ProcessEngineConfiguration processEngineConfiguration,
                                                                 OperatonEngineBpmnConfig operatonEngineBpmnConfig,
                                                                 All<ProcessEngineConfigurator> engineConfigurators) {
        return new KoraProcessEngine(processEngineConfiguration, operatonEngineBpmnConfig, engineConfigurators);
    }

    default ProcessEngineConfigurator operatonEngineBpmnKoraAdminUserConfigurator(OperatonEngineBpmnConfig operatonEngineBpmnConfig, OperatonEngineDataSource engineDataSource) {
        return new AdminUserProcessEngineConfigurator(operatonEngineBpmnConfig, engineDataSource);
    }

    default ProcessEngineConfigurator operatonEngineBpmnKoraResourceDeploymentConfigurator(OperatonEngineBpmnConfig operatonEngineBpmnConfig) {
        return new DeploymentProcessEngineConfigurator(operatonEngineBpmnConfig);
    }

    default ProcessEngineConfigurator operatonEngineBpmnKoraProcessEngineTwoStageOperatonConfigurator(ProcessEngineConfiguration engineConfiguration,
                                                                                                    OperatonEngineBpmnConfig operatonEngineBpmnConfig,
                                                                                                    JobExecutor jobExecutor) {
        return new SecondStageKoraProcessEngineConfigurator(engineConfiguration, operatonEngineBpmnConfig, jobExecutor);
    }

    @Root
    default Lifecycle operatonKoraProcessEngineParallelInitializer(ProcessEngine processEngine,
                                                                  OperatonEngineBpmnConfig operatonEngineBpmnConfig,
                                                                  ProcessEngineConfiguration processEngineConfiguration,
                                                                  All<ProcessEngineConfigurator> engineConfigurators) {
        return new KoraProcessEngineParallelInitializer(processEngine, operatonEngineBpmnConfig, processEngineConfiguration, engineConfigurators);
    }

    default RuntimeService operatonEngineBpmnRuntimeService(ProcessEngine processEngine) {
        return processEngine.getRuntimeService();
    }

    default RepositoryService operatonEngineBpmnRepositoryService(ProcessEngine processEngine) {
        return processEngine.getRepositoryService();
    }

    default ManagementService operatonEngineBpmnManagementService(ProcessEngine processEngine) {
        return processEngine.getManagementService();
    }

    default AuthorizationService operatonEngineBpmnAuthorizationService(ProcessEngine processEngine) {
        return processEngine.getAuthorizationService();
    }

    default DecisionService operatonEngineBpmnDecisionService(ProcessEngine processEngine) {
        return processEngine.getDecisionService();
    }

    default ExternalTaskService operatonEngineBpmnExternalTaskService(ProcessEngine processEngine) {
        return processEngine.getExternalTaskService();
    }

    default FilterService operatonEngineBpmnFilterService(ProcessEngine processEngine) {
        return processEngine.getFilterService();
    }

    default FormService operatonEngineBpmnFormService(ProcessEngine processEngine) {
        return processEngine.getFormService();
    }

    default TaskService operatonEngineBpmnTaskService(ProcessEngine processEngine) {
        return processEngine.getTaskService();
    }

    default HistoryService operatonEngineBpmnHistoryService(ProcessEngine processEngine) {
        return processEngine.getHistoryService();
    }

    default IdentityService operatonEngineBpmnIdentityService(ProcessEngine processEngine) {
        return processEngine.getIdentityService();
    }
}
