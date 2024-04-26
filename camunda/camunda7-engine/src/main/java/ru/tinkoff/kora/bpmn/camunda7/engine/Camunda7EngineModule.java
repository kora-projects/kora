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

public interface Camunda7EngineModule {

    default Camunda7EngineConfig camundaEngineConfig(Config config, ConfigValueExtractor<Camunda7EngineConfig> extractor) {
        return extractor.extract(config.get("camunda7.engine"));
    }

    @Tag(Camunda7.class)
    @DefaultComponent
    default DataSource camundaDataSource(DataSource dataSource) {
        return dataSource;
    }

    @Tag(Camunda7.class)
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
    default JobExecutor camundaKoraJobExecutor(Camunda7EngineConfig engineConfig) {
        return new KoraJobExecutor(engineConfig);
    }

    default ReadinessProbe camundaReadinessProbe(JobExecutor jobExecutor) {
        return new Camunda7JobExecutorReadinessProbe(jobExecutor);
    }

    @DefaultComponent
    default ELResolver camundaKoraELResolver(All<Camunda7Delegate> components,
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
    default Camunda7Version camundaPackageVersion() {
        return new Camunda7Version(Optional.ofNullable(ProcessEngine.class.getPackage().getImplementationVersion())
            .map(String::trim)
            .orElse(null));
    }

    @DefaultComponent
    default KoraResolverFactory camundaKoraComponentResolverFactory(All<Camunda7Delegate> components,
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
                                                                                 @Tag(Camunda7.class) JdbcConnectionFactory jdbcConnectionFactory,
                                                                                 @Tag(Camunda7.class) DataSource dataSource,
                                                                                 Camunda7EngineConfig camundaEngineConfig,
                                                                                 KoraResolverFactory componentResolverFactory,
                                                                                 Camunda7Version camundaVersion) {
        return new KoraProcessEngineConfiguration(jobExecutor, telemetryRegistry, idGenerator, koraExpressionManager, artifactFactory, plugins, jdbcConnectionFactory, dataSource, camundaEngineConfig, componentResolverFactory, camundaVersion);
    }

    @DefaultComponent
    default KoraProcessEngine camundaKoraProcessEngine(KoraProcessEngineConfiguration processEngineConfiguration) {
        return new KoraProcessEngine(processEngineConfiguration);
    }

    default ProcessEngineConfigurator camundaKoraAdminUserConfigurator(Camunda7EngineConfig camundaEngineConfig, @Tag(Camunda7.class) JdbcConnectionFactory jdbcConnectionFactory) {
        return new AdminUserProcessEngineConfigurator(camundaEngineConfig, jdbcConnectionFactory);
    }

    default ProcessEngineConfigurator camundaKoraFilterAllTaskConfigurator(Camunda7EngineConfig camundaEngineConfig) {
        return new FilterProcessEngineConfigurator(camundaEngineConfig);
    }

    default ProcessEngineConfigurator camundaKoraLicenseKeyConfigurator(Camunda7EngineConfig camundaEngineConfig, Camunda7Version camundaVersion) {
        return new LicenseKeyProcessEngineConfigurator(camundaEngineConfig, camundaVersion);
    }

    default ProcessEngineConfigurator camundaKoraResourceDeploymentConfigurator(Camunda7EngineConfig camundaEngineConfig) {
        return new DeploymentProcessEngineConfigurator(camundaEngineConfig);
    }

    default ProcessEngineConfigurator camundaKoraProcessEngineTwoStageCamundaConfigurator(KoraProcessEngineConfiguration engineConfiguration,
                                                                                          Camunda7EngineConfig camundaEngineConfig,
                                                                                          JobExecutor jobExecutor) {
        return new StatementsProcessEngineConfigurator(engineConfiguration, camundaEngineConfig, jobExecutor);
    }

    @Root
    default KoraProcessEngineConfigurator camundaKoraProcessEngineConfigurator(ProcessEngine processEngine, All<ProcessEngineConfigurator> camundaConfigurators) {
        return new KoraProcessEngineConfigurator(processEngine, camundaConfigurators);
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
