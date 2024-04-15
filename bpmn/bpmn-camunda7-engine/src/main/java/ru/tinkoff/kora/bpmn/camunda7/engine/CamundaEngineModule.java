package ru.tinkoff.kora.bpmn.camunda7.engine;

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
import ru.tinkoff.kora.bpmn.camunda7.engine.configurator.*;
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
    default DataSource camundaDataSource(DataSource dataSource) {
        return dataSource;
    }

    @Tag(Camunda.class)
    @DefaultComponent
    default JdbcConnectionFactory camundaJdbcConnectionFactory(JdbcConnectionFactory jdbcConnectionFactory) {
        return jdbcConnectionFactory;
    }

    @DefaultComponent
    default IdGenerator camundaIdGenerator() {
        return new StrongUuidGenerator();
    }

    @DefaultComponent
    default TelemetryRegistry camundaKoraTelemetryRegistry(@Nullable ApplicationServerImpl applicationServer) {
        return new KoraTelemetryRegistry(applicationServer);
    }

    @DefaultComponent
    default JobExecutor camundaKoraJobExecutor(CamundaEngineConfig engineConfig) {
        return new KoraJobExecutor(engineConfig);
    }

    default ReadinessProbe camundaReadinessProbe(JobExecutor jobExecutor) {
        return new CamundaReadinessProbe(jobExecutor);
    }

    @DefaultComponent
    default ELResolver camundaKoraELResolver(All<CamundaDelegate> components,
                                             All<JavaDelegate> delegates) {
        return new KoraELResolver(components, delegates);
    }

    @DefaultComponent
    default JuelExpressionManager camundaKoraExpressionManager(ELResolver koraELResolver) {
        return new KoraExpressionManager(koraELResolver);
    }

    @DefaultComponent
    default ArtifactFactory camundaKoraArtifactFactory(All<JavaDelegate> delegates) {
        return new KoraArtifactFactory(delegates);
    }

    @DefaultComponent
    default CamundaVersion camundaPackageVersion() {
        return new CamundaVersion(Optional.ofNullable(ProcessEngine.class.getPackage().getImplementationVersion())
            .map(String::trim)
            .orElse(null));
    }

    @DefaultComponent
    default KoraResolverFactory camundaKoraComponentResolverFactory(All<CamundaDelegate> components,
                                                                    All<JavaDelegate> delegates) {
        return new KoraResolverFactory(components, delegates);
    }

    @DefaultComponent
    default KoraProcessEngineConfiguration camundaKoraProcessEngineConfiguration(JobExecutor jobExecutor,
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
    default KoraProcessEngine camundaKoraProcessEngine(KoraProcessEngineConfiguration processEngineConfiguration) {
        return new KoraProcessEngine(processEngineConfiguration);
    }

    default CamundaConfigurator camundaKoraAdminUserConfigurator(CamundaEngineConfig camundaEngineConfig, @Tag(Camunda.class) JdbcConnectionFactory jdbcConnectionFactory) {
        return new AdminUserCamundaConfigurator(camundaEngineConfig, jdbcConnectionFactory);
    }

    default CamundaConfigurator camundaKoraFilterAllTaskConfigurator(CamundaEngineConfig camundaEngineConfig) {
        return new FilterAllTaskCamundaConfigurator(camundaEngineConfig);
    }

    default CamundaConfigurator camundaKoraLicenseKeyConfigurator(CamundaEngineConfig camundaEngineConfig, CamundaVersion camundaVersion) {
        return new LicenseKeyCamundaConfigurator(camundaEngineConfig, camundaVersion);
    }

    default CamundaConfigurator camundaKoraResourceDeploymentConfigurator(CamundaEngineConfig camundaEngineConfig) {
        return new ResourceDeploymentCamundaConfigurator(camundaEngineConfig);
    }

    default CamundaConfigurator camundaKoraProcessEngineTwoStageCamundaConfigurator(KoraProcessEngineConfiguration engineConfiguration,
                                                                                    CamundaEngineConfig camundaEngineConfig,
                                                                                    JobExecutor jobExecutor) {
        return new ProcessEngineStatementCamundaConfigurator(engineConfiguration, camundaEngineConfig, jobExecutor);
    }

    @Root
    default CamundaConfiguratorTrigger camundaKoraConfigurator(ProcessEngine processEngine, All<CamundaConfigurator> camundaConfigurators) {
        return new CamundaConfiguratorTrigger(processEngine, camundaConfigurators);
    }

    default RuntimeService camundaRuntimeService(ProcessEngine processEngine) {
        return processEngine.getRuntimeService();
    }

    default RepositoryService camundaRepositoryService(ProcessEngine processEngine) {
        return processEngine.getRepositoryService();
    }

    default ManagementService camundaManagementService(ProcessEngine processEngine) {
        return processEngine.getManagementService();
    }

    default AuthorizationService camundaAuthorizationService(ProcessEngine processEngine) {
        return processEngine.getAuthorizationService();
    }

    default DecisionService camundaDecisionService(ProcessEngine processEngine) {
        return processEngine.getDecisionService();
    }

    default ExternalTaskService camundaExternalTaskService(ProcessEngine processEngine) {
        return processEngine.getExternalTaskService();
    }

    default FilterService camundaFilterService(ProcessEngine processEngine) {
        return processEngine.getFilterService();
    }

    default FormService camundaFormService(ProcessEngine processEngine) {
        return processEngine.getFormService();
    }

    default TaskService camundaTaskService(ProcessEngine processEngine) {
        return processEngine.getTaskService();
    }

    default HistoryService camundaHistoryService(ProcessEngine processEngine) {
        return processEngine.getHistoryService();
    }

    default IdentityService camundaIdentityService(ProcessEngine processEngine) {
        return processEngine.getIdentityService();
    }
}
