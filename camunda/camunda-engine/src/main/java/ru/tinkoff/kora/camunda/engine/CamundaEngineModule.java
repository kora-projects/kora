package ru.tinkoff.kora.camunda.engine;

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
import ru.tinkoff.kora.camunda.engine.configurator.*;
import ru.tinkoff.kora.camunda.engine.telemetry.*;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.common.readiness.ReadinessProbe;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.database.jdbc.JdbcConnectionFactory;

import javax.sql.DataSource;
import java.util.Optional;

public interface CamundaEngineModule {

    default CamundaEngineConfig camundaEngineConfig(Config config, ConfigValueExtractor<CamundaEngineConfig> extractor) {
        return extractor.extract(config.get("camunda.engine"));
    }

    @Tag(Camunda.class)
    @DefaultComponent
    default DataSource camundaEngineDataSource(DataSource dataSource) {
        return dataSource;
    }

    @Tag(Camunda.class)
    @DefaultComponent
    default JdbcConnectionFactory camundaEngineJdbcConnectionFactory(JdbcConnectionFactory jdbcConnectionFactory) {
        return jdbcConnectionFactory;
    }

    @DefaultComponent
    default IdGenerator camundaEngineIdGenerator() {
        return new StrongUuidGenerator();
    }

    @DefaultComponent
    default TelemetryRegistry camundaEngineKoraTelemetryRegistry(@Nullable ApplicationServerImpl applicationServer) {
        return new KoraEngineTelemetryRegistry(applicationServer);
    }

    @DefaultComponent
    default JobExecutor camundaEngineKoraJobExecutor(CamundaEngineConfig engineConfig) {
        return new KoraJobExecutor(engineConfig);
    }

    default ReadinessProbe camundaEngineReadinessProbe(JobExecutor jobExecutor) {
        return new JobExecutorReadinessProbe(jobExecutor);
    }

    @DefaultComponent
    default CamundaEngineLoggerFactory camundaEngineLoggerFactory() {
        return new DefaultCamundaEngineLoggerFactory();
    }

    @DefaultComponent
    default CamundaEngineTelemetryFactory camundaEngineTelemetryFactory(@Nullable CamundaEngineLoggerFactory logger,
                                                                        @Nullable CamundaEngineMetricsFactory metrics,
                                                                        @Nullable CamundaEngineTracerFactory tracer) {
        return new DefaultCamundaEngineTelemetryFactory(logger, metrics, tracer);
    }

    @DefaultComponent
    default KoraDelegateWrapperFactory koraJavaDelegateTelemetryWrapper(CamundaEngineTelemetryFactory telemetryFactory,
                                                                        CamundaEngineConfig camundaEngineConfig) {
        return delegate -> {
            var telemetry = telemetryFactory.get(camundaEngineConfig.telemetry());
            return execution -> {
                var telemetryContext = telemetry.get(delegate.getClass().getCanonicalName(), execution);
                try {
                    delegate.execute(execution);
                    telemetryContext.close();
                } catch (Exception e) {
                    telemetryContext.close(e);
                    throw e;
                }
            };
        };
    }

    @DefaultComponent
    default ELResolver camundaEngineKoraELResolver(KoraDelegateWrapperFactory wrapperFactory,
                                                   All<KoraDelegate> koraDelegates,
                                                   All<JavaDelegate> javaDelegates) {
        return new KoraELResolver(wrapperFactory, koraDelegates, javaDelegates);
    }

    @DefaultComponent
    default JuelExpressionManager camundaEngineKoraExpressionManager(ELResolver koraELResolver) {
        return new KoraExpressionManager(koraELResolver);
    }

    @DefaultComponent
    default ArtifactFactory camundaEngineKoraArtifactFactory(KoraDelegateWrapperFactory wrapperFactory,
                                                             All<KoraDelegate> koraDelegates,
                                                             All<JavaDelegate> javaDelegates) {
        return new KoraArtifactFactory(wrapperFactory, koraDelegates, javaDelegates);
    }

    @DefaultComponent
    default CamundaVersion camundaEnginePackageVersion() {
        return new CamundaVersion(Optional.ofNullable(ProcessEngine.class.getPackage().getImplementationVersion())
            .map(String::trim)
            .orElse(null));
    }

    @DefaultComponent
    default KoraResolverFactory camundaEngineKoraComponentResolverFactory(KoraDelegateWrapperFactory wrapperFactory,
                                                                          All<KoraDelegate> koraDelegates,
                                                                          All<JavaDelegate> javaDelegates) {
        return new KoraResolverFactory(wrapperFactory, koraDelegates, javaDelegates);
    }

    @DefaultComponent
    default KoraProcessEngineConfiguration camundaEngineKoraProcessEngineConfiguration(JobExecutor jobExecutor,
                                                                                       TelemetryRegistry telemetryRegistry,
                                                                                       IdGenerator idGenerator,
                                                                                       JuelExpressionManager koraExpressionManager,
                                                                                       ArtifactFactory artifactFactory,
                                                                                       All<ProcessEnginePlugin> plugins,
                                                                                       @Tag(Camunda.class) JdbcConnectionFactory jdbcConnectionFactory,
                                                                                       @Tag(Camunda.class) DataSource dataSource,
                                                                                       CamundaEngineConfig camundaEngineConfig,
                                                                                       KoraResolverFactory componentResolverFactory,
                                                                                       CamundaVersion camundaVersion) {
        return new KoraProcessEngineConfiguration(jobExecutor, telemetryRegistry, idGenerator, koraExpressionManager, artifactFactory, plugins, jdbcConnectionFactory, dataSource, camundaEngineConfig, componentResolverFactory, camundaVersion);
    }

    @DefaultComponent
    default KoraProcessEngine camundaEngineKoraProcessEngine(KoraProcessEngineConfiguration processEngineConfiguration) {
        return new KoraProcessEngine(processEngineConfiguration);
    }

    default ProcessEngineConfigurator camundaEngineKoraAdminUserConfigurator(CamundaEngineConfig camundaEngineConfig, @Tag(Camunda.class) JdbcConnectionFactory jdbcConnectionFactory) {
        return new AdminUserProcessEngineConfigurator(camundaEngineConfig, jdbcConnectionFactory);
    }

    default ProcessEngineConfigurator camundaEngineKoraFilterAllTaskConfigurator(CamundaEngineConfig camundaEngineConfig) {
        return new FilterProcessEngineConfigurator(camundaEngineConfig);
    }

    default ProcessEngineConfigurator camundaEngineKoraLicenseKeyConfigurator(CamundaEngineConfig camundaEngineConfig, CamundaVersion camundaVersion) {
        return new LicenseKeyProcessEngineConfigurator(camundaEngineConfig, camundaVersion);
    }

    default ProcessEngineConfigurator camundaEngineKoraResourceDeploymentConfigurator(CamundaEngineConfig camundaEngineConfig) {
        return new DeploymentProcessEngineConfigurator(camundaEngineConfig);
    }

    default ProcessEngineConfigurator camundaEngineKoraProcessEngineTwoStageCamundaConfigurator(KoraProcessEngineConfiguration engineConfiguration,
                                                                                                CamundaEngineConfig camundaEngineConfig,
                                                                                                JobExecutor jobExecutor) {
        return new StatementsProcessEngineConfigurator(engineConfiguration, camundaEngineConfig, jobExecutor);
    }

    @Root
    default KoraProcessEngineConfigurator camundaEngineKoraProcessEngineConfigurator(ProcessEngine processEngine, All<ProcessEngineConfigurator> camundaConfigurators) {
        return new KoraProcessEngineConfigurator(processEngine, camundaConfigurators);
    }

    default RuntimeService camundaEngineRuntimeService(ProcessEngine processEngine) {
        return processEngine.getRuntimeService();
    }

    default RepositoryService camundaEngineRepositoryService(ProcessEngine processEngine) {
        return processEngine.getRepositoryService();
    }

    default ManagementService camundaEngineManagementService(ProcessEngine processEngine) {
        return processEngine.getManagementService();
    }

    default AuthorizationService camundaEngineAuthorizationService(ProcessEngine processEngine) {
        return processEngine.getAuthorizationService();
    }

    default DecisionService camundaEngineDecisionService(ProcessEngine processEngine) {
        return processEngine.getDecisionService();
    }

    default ExternalTaskService camundaEngineExternalTaskService(ProcessEngine processEngine) {
        return processEngine.getExternalTaskService();
    }

    default FilterService camundaEngineFilterService(ProcessEngine processEngine) {
        return processEngine.getFilterService();
    }

    default FormService camundaEngineFormService(ProcessEngine processEngine) {
        return processEngine.getFormService();
    }

    default TaskService camundaEngineTaskService(ProcessEngine processEngine) {
        return processEngine.getTaskService();
    }

    default HistoryService camundaEngineHistoryService(ProcessEngine processEngine) {
        return processEngine.getHistoryService();
    }

    default IdentityService camundaEngineIdentityService(ProcessEngine processEngine) {
        return processEngine.getIdentityService();
    }
}
