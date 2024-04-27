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
import ru.tinkoff.kora.bpmn.camunda7.engine.telemetry.KoraTelemetryRegistry;
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

    default Camunda7EngineConfig camunda7EngineConfig(Config config, ConfigValueExtractor<Camunda7EngineConfig> extractor) {
        return extractor.extract(config.get("camunda7.engine"));
    }

    @Tag(Camunda7.class)
    @DefaultComponent
    default DataSource camunda7DataSource(DataSource dataSource) {
        return dataSource;
    }

    @Tag(Camunda7.class)
    @DefaultComponent
    default JdbcConnectionFactory camunda7JdbcConnectionFactory(JdbcConnectionFactory jdbcConnectionFactory) {
        return jdbcConnectionFactory;
    }

    @DefaultComponent
    default IdGenerator camunda7IdGenerator() {
        return new StrongUuidGenerator();
    }

    @DefaultComponent
    default TelemetryRegistry camunda7KoraTelemetryRegistry(@Nullable ApplicationServerImpl applicationServer) {
        return new KoraTelemetryRegistry(applicationServer);
    }

    @DefaultComponent
    default JobExecutor camunda7KoraJobExecutor(Camunda7EngineConfig engineConfig) {
        return new KoraJobExecutor(engineConfig);
    }

    default ReadinessProbe camunda7ReadinessProbe(JobExecutor jobExecutor) {
        return new JobExecutorReadinessProbe(jobExecutor);
    }

    @DefaultComponent
    default ELResolver camunda7KoraELResolver(All<KoraDelegate> koraDelegates,
                                              All<JavaDelegate> javaDelegates) {
        return new KoraELResolver(koraDelegates, javaDelegates);
    }

    @DefaultComponent
    default JuelExpressionManager camunda7KoraExpressionManager(ELResolver koraELResolver) {
        return new KoraExpressionManager(koraELResolver);
    }

    @DefaultComponent
    default ArtifactFactory camunda7KoraArtifactFactory(All<KoraDelegate> koraDelegates,
                                                        All<JavaDelegate> javaDelegates) {
        return new KoraArtifactFactory(koraDelegates, javaDelegates);
    }

    @DefaultComponent
    default Camunda7Version camunda7PackageVersion() {
        return new Camunda7Version(Optional.ofNullable(ProcessEngine.class.getPackage().getImplementationVersion())
            .map(String::trim)
            .orElse(null));
    }

    @DefaultComponent
    default KoraResolverFactory camunda7KoraComponentResolverFactory(All<KoraDelegate> koraDelegates,
                                                                     All<JavaDelegate> javaDelegates) {
        return new KoraResolverFactory(koraDelegates, javaDelegates);
    }

    @DefaultComponent
    default KoraProcessEngineConfiguration camunda7KoraProcessEngineConfiguration(JobExecutor jobExecutor,
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
    default KoraProcessEngine camunda7KoraProcessEngine(KoraProcessEngineConfiguration processEngineConfiguration) {
        return new KoraProcessEngine(processEngineConfiguration);
    }

    default ProcessEngineConfigurator camunda7KoraAdminUserConfigurator(Camunda7EngineConfig camundaEngineConfig, @Tag(Camunda7.class) JdbcConnectionFactory jdbcConnectionFactory) {
        return new AdminUserProcessEngineConfigurator(camundaEngineConfig, jdbcConnectionFactory);
    }

    default ProcessEngineConfigurator camunda7KoraFilterAllTaskConfigurator(Camunda7EngineConfig camundaEngineConfig) {
        return new FilterProcessEngineConfigurator(camundaEngineConfig);
    }

    default ProcessEngineConfigurator camunda7KoraLicenseKeyConfigurator(Camunda7EngineConfig camundaEngineConfig, Camunda7Version camundaVersion) {
        return new LicenseKeyProcessEngineConfigurator(camundaEngineConfig, camundaVersion);
    }

    default ProcessEngineConfigurator camunda7KoraResourceDeploymentConfigurator(Camunda7EngineConfig camundaEngineConfig) {
        return new DeploymentProcessEngineConfigurator(camundaEngineConfig);
    }

    default ProcessEngineConfigurator camunda7KoraProcessEngineTwoStageCamundaConfigurator(KoraProcessEngineConfiguration engineConfiguration,
                                                                                           Camunda7EngineConfig camundaEngineConfig,
                                                                                           JobExecutor jobExecutor) {
        return new StatementsProcessEngineConfigurator(engineConfiguration, camundaEngineConfig, jobExecutor);
    }

    @Root
    default KoraProcessEngineConfigurator camunda7KoraProcessEngineConfigurator(ProcessEngine processEngine, All<ProcessEngineConfigurator> camundaConfigurators) {
        return new KoraProcessEngineConfigurator(processEngine, camundaConfigurators);
    }

    default RuntimeService camunda7RuntimeService(ProcessEngine processEngine) {
        return processEngine.getRuntimeService();
    }

    default RepositoryService camunda7RepositoryService(ProcessEngine processEngine) {
        return processEngine.getRepositoryService();
    }

    default ManagementService camunda7ManagementService(ProcessEngine processEngine) {
        return processEngine.getManagementService();
    }

    default AuthorizationService camunda7AuthorizationService(ProcessEngine processEngine) {
        return processEngine.getAuthorizationService();
    }

    default DecisionService camunda7DecisionService(ProcessEngine processEngine) {
        return processEngine.getDecisionService();
    }

    default ExternalTaskService camunda7ExternalTaskService(ProcessEngine processEngine) {
        return processEngine.getExternalTaskService();
    }

    default FilterService camunda7FilterService(ProcessEngine processEngine) {
        return processEngine.getFilterService();
    }

    default FormService camunda7FormService(ProcessEngine processEngine) {
        return processEngine.getFormService();
    }

    default TaskService camunda7TaskService(ProcessEngine processEngine) {
        return processEngine.getTaskService();
    }

    default HistoryService camunda7HistoryService(ProcessEngine processEngine) {
        return processEngine.getHistoryService();
    }

    default IdentityService camunda7IdentityService(ProcessEngine processEngine) {
        return processEngine.getIdentityService();
    }
}
