package ru.tinkoff.kora.camunda.engine.bpmn;

import jakarta.annotation.Nullable;
import org.camunda.bpm.engine.*;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.impl.cfg.IdGenerator;
import org.camunda.bpm.engine.impl.cfg.ProcessEnginePlugin;
import org.camunda.bpm.engine.impl.el.JuelExpressionManager;
import org.camunda.bpm.engine.impl.jobexecutor.JobExecutor;
import org.camunda.bpm.engine.impl.persistence.StrongUuidGenerator;
import org.camunda.bpm.engine.impl.telemetry.TelemetryRegistry;
import org.camunda.bpm.engine.impl.telemetry.dto.ApplicationServerImpl;
import org.camunda.bpm.impl.juel.jakarta.el.ELResolver;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.application.graph.internal.loom.VirtualThreadExecutorHolder;
import ru.tinkoff.kora.camunda.engine.bpmn.configurator.AdminUserProcessEngineConfigurator;
import ru.tinkoff.kora.camunda.engine.bpmn.configurator.DeploymentProcessEngineConfigurator;
import ru.tinkoff.kora.camunda.engine.bpmn.configurator.ProcessEngineConfigurator;
import ru.tinkoff.kora.camunda.engine.bpmn.configurator.SecondStageKoraProcessEngineConfigurator;
import ru.tinkoff.kora.camunda.engine.bpmn.telemetry.*;
import ru.tinkoff.kora.camunda.engine.bpmn.transaction.CamundaTransactionManager;
import ru.tinkoff.kora.camunda.engine.bpmn.transaction.JdbcCamundaTransactionManager;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.common.readiness.ReadinessProbe;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public interface CamundaEngineBpmnModule {

    default CamundaEngineBpmnConfig camundaEngineBpmnConfig(Config config, ConfigValueExtractor<CamundaEngineBpmnConfig> extractor) {
        return extractor.extract(config.get("camunda.engine.bpmn"));
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
    default TelemetryRegistry camundaEngineBpmnKoraTelemetryRegistry(@Nullable ApplicationServerImpl applicationServer) {
        return new KoraEngineTelemetryRegistry(applicationServer);
    }

    @DefaultComponent
    default JobExecutor camundaEngineBpmnKoraJobExecutor(CamundaEngineBpmnConfig engineConfig) {
        if (engineConfig.jobExecutor().virtualThreadsEnabled()) {
            if (VirtualThreadExecutorHolder.status() != VirtualThreadExecutorHolder.VirtualThreadStatus.ENABLED) {
                throw new IllegalStateException("Camunda BPMN Engine starting failed cause Virtual Threads are not available but enabled in configuration, please check that you are using Java 21+ or disable Virtual Threads for Camunda BPMN Engine in configuration.");
            }

            return new KoraVirtualThreadJobExecutor(VirtualThreadExecutorHolder.executor(), engineConfig);
        } else {
            return new KoraThreadPoolJobExecutor(engineConfig);
        }
    }

    default ReadinessProbe camundaEngineBpmnReadinessProbe(JobExecutor jobExecutor) {
        return new JobExecutorReadinessProbe(jobExecutor);
    }

    @DefaultComponent
    default CamundaEngineBpmnLoggerFactory camundaEngineBpmnLoggerFactory() {
        return new DefaultCamundaEngineBpmnLoggerFactory();
    }

    @DefaultComponent
    default CamundaEngineBpmnTelemetryFactory camundaEngineBpmnTelemetryFactory(@Nullable CamundaEngineBpmnLoggerFactory logger,
                                                                                @Nullable CamundaEngineBpmnMetricsFactory metrics,
                                                                                @Nullable CamundaEngineBpmnTracerFactory tracer) {
        return new DefaultCamundaEngineBpmnTelemetryFactory(logger, metrics, tracer);
    }

    @DefaultComponent
    default KoraDelegateWrapperFactory koraJavaDelegateTelemetryWrapper(CamundaEngineBpmnTelemetryFactory telemetryFactory,
                                                                        CamundaEngineBpmnConfig camundaEngineBpmnConfig) {
        return delegate -> {
            var telemetry = telemetryFactory.get(camundaEngineBpmnConfig.telemetry());
            return execution -> {
                var current = Context.current();
                var fork = current.fork();
                fork.inject();

                var telemetryContext = telemetry.get(delegate.getClass().getCanonicalName(), execution);
                try {
                    delegate.execute(execution);
                    telemetryContext.close();
                } catch (Exception e) {
                    telemetryContext.close(e);
                    throw e;
                } finally {
                    current.inject();
                }
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
                                                                                       TelemetryRegistry telemetryRegistry,
                                                                                       IdGenerator idGenerator,
                                                                                       JuelExpressionManager koraExpressionManager,
                                                                                       ArtifactFactory artifactFactory,
                                                                                       All<ProcessEnginePlugin> plugins,
                                                                                       CamundaEngineDataSource camundaEngineDataSource,
                                                                                       CamundaEngineBpmnConfig camundaEngineBpmnConfig,
                                                                                       KoraResolverFactory componentResolverFactory,
                                                                                       CamundaVersion camundaVersion,
                                                                                       @Nullable CamundaEngineBpmnMetricsFactory metricsFactory) {
        return new KoraProcessEngineConfiguration(jobExecutor, telemetryRegistry, idGenerator, koraExpressionManager, artifactFactory, plugins, camundaEngineDataSource, camundaEngineBpmnConfig, componentResolverFactory, camundaVersion, metricsFactory);
    }

    @Tag(CamundaBpmn.class)
    default Executor camundaEngineInitializeExecutor() {
        return VirtualThreadExecutorHolder.status() == VirtualThreadExecutorHolder.VirtualThreadStatus.ENABLED
            ? VirtualThreadExecutorHolder.executor()
            : Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
    }

    @Root
    @DefaultComponent
    default KoraProcessEngine camundaEngineBpmnKoraProcessEngine(ProcessEngineConfiguration processEngineConfiguration,
                                                                      CamundaEngineBpmnConfig camundaEngineBpmnConfig,
                                                                      All<ProcessEngineConfigurator> camundaConfigurators,
                                                                      @Tag(CamundaBpmn.class) Executor executor) {
        return new KoraProcessEngine(processEngineConfiguration, camundaEngineBpmnConfig, camundaConfigurators, executor);
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
                                                                  All<ProcessEngineConfigurator> camundaConfigurators,
                                                                  @Tag(CamundaBpmn.class) Executor executor) {
        return new KoraProcessEngineParallelInitializer(processEngine, camundaEngineBpmnConfig, processEngineConfiguration, camundaConfigurators, executor);
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
