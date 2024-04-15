package ru.tinkoff.kora.bpmn.camunda7.engine;

import org.apache.ibatis.builder.xml.XMLConfigBuilder;
import org.camunda.bpm.engine.ArtifactFactory;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.history.*;
import org.camunda.bpm.engine.impl.*;
import org.camunda.bpm.engine.impl.cfg.IdGenerator;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cfg.ProcessEnginePlugin;
import org.camunda.bpm.engine.impl.cmmn.CaseServiceImpl;
import org.camunda.bpm.engine.impl.cmmn.entity.repository.CaseDefinitionQueryImpl;
import org.camunda.bpm.engine.impl.cmmn.entity.runtime.CaseExecutionQueryImpl;
import org.camunda.bpm.engine.impl.cmmn.entity.runtime.CaseInstanceQueryImpl;
import org.camunda.bpm.engine.impl.el.JuelExpressionManager;
import org.camunda.bpm.engine.impl.interceptor.*;
import org.camunda.bpm.engine.impl.jobexecutor.JobExecutor;
import org.camunda.bpm.engine.impl.telemetry.TelemetryRegistry;
import org.camunda.bpm.engine.repository.CaseDefinition;
import org.camunda.bpm.engine.repository.CaseDefinitionQuery;
import org.camunda.bpm.engine.runtime.CaseExecution;
import org.camunda.bpm.engine.runtime.CaseExecutionQuery;
import org.camunda.bpm.engine.runtime.CaseInstance;
import org.camunda.bpm.engine.runtime.CaseInstanceQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.bpmn.camunda7.engine.transaction.KoraTransactionContextFactory;
import ru.tinkoff.kora.bpmn.camunda7.engine.transaction.KoraTransactionInterceptor;
import ru.tinkoff.kora.database.jdbc.JdbcConnectionFactory;

import javax.sql.DataSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

import static java.util.Collections.emptyList;
import static org.camunda.bpm.engine.impl.history.HistoryLevel.HISTORY_LEVEL_FULL;

public class KoraProcessEngineConfiguration extends ProcessEngineConfigurationImpl {

    private static final Logger logger = LoggerFactory.getLogger(KoraProcessEngineConfiguration.class);

    private final JobExecutor jobExecutor;
    private final TelemetryRegistry telemetryRegistry;
    private final KoraResolverFactory componentResolverFactory;
    private final JdbcConnectionFactory connectionFactory;
    private final CamundaVersion camundaVersion;
    private final CamundaEngineConfig engineConfig;
    private final List<ProcessEnginePlugin> plugins;

    public KoraProcessEngineConfiguration(JobExecutor jobExecutor,
                                          TelemetryRegistry telemetryRegistry,
                                          IdGenerator idGenerator,
                                          JuelExpressionManager koraExpressionManager,
                                          ArtifactFactory artifactFactory,
                                          List<ProcessEnginePlugin> plugins,
                                          JdbcConnectionFactory jdbcConnectionFactory,
                                          DataSource dataSource,
                                          CamundaEngineConfig engineConfig,
                                          KoraResolverFactory componentResolverFactory,
                                          CamundaVersion camundaVersion) {
        this.jobExecutor = jobExecutor;
        this.telemetryRegistry = telemetryRegistry;
        this.componentResolverFactory = componentResolverFactory;
        this.idGenerator = idGenerator;
        this.artifactFactory = artifactFactory;
        this.plugins = plugins;
        this.engineConfig = engineConfig;
        this.connectionFactory = jdbcConnectionFactory;
        this.camundaVersion = camundaVersion;

        setDefaultCharset(StandardCharsets.UTF_8);

        setDataSource(dataSource);
        setTransactionsExternallyManaged(true);
        setIdGenerator(idGenerator);
        setExpressionManager(koraExpressionManager);
        setArtifactFactory(artifactFactory);

        configureDefaultValues();
        configureMetricsAndTelemetry();
        registerProcessEnginePlugins();

        mockUnsupportedCmmnMethods();
    }

    @Override
    protected void initTransactionContextFactory() {
        if (transactionContextFactory == null) {
            transactionContextFactory = new KoraTransactionContextFactory(connectionFactory);
        }
    }

    @Override
    protected void initJobExecutor() {
        setJobExecutor(jobExecutor);
        super.initJobExecutor();
    }

    @Override
    protected void initScripting() {
        super.initScripting();
        getResolverFactories().add(componentResolverFactory);
    }

    @Override
    protected Collection<? extends CommandInterceptor> getDefaultCommandInterceptorsTxRequired() {
        return getCommandInterceptors(false);
    }

    @Override
    protected Collection<? extends CommandInterceptor> getDefaultCommandInterceptorsTxRequiresNew() {
        return getCommandInterceptors(true);
    }

    protected List<CommandInterceptor> getCommandInterceptors(boolean requiresNew) {
        return Arrays.asList(
            new LogInterceptor(),
            new CommandCounterInterceptor(this),
            new ProcessApplicationContextInterceptor(this),
            new KoraTransactionInterceptor(connectionFactory),
            new CommandContextInterceptor(commandContextFactory, this, requiresNew)
        );
    }

    protected void configureMetricsAndTelemetry() {
        setMetricsEnabled(engineConfig.metrics().metricsEnabled());
        setTaskMetricsEnabled(engineConfig.metrics().taskMetricsEnabled());

        if (engineConfig.telemetry().telemetryEnabled()) {
            setTelemetryRegistry(telemetryRegistry);
            setInitializeTelemetry(engineConfig.telemetry().telemetryEnabled());
            setTelemetryReporterActivate(engineConfig.telemetry().telemetryReporterEnabled());

            if (camundaVersion.version() == null) {
                logger.warn("Disabling TelemetryReporter because required information 'Camunda Version' is not available.");
                setTelemetryReporterActivate(false);
            }
        }
    }

    protected void configureDefaultValues() {
        setJobExecutorActivate(true);
        setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        setEnforceHistoryTimeToLive(false);
    }

    protected void registerProcessEnginePlugins() {
        if (!plugins.isEmpty()) {
            logger.info("Registering process engine plugins: {}", plugins);
            setProcessEnginePlugins(plugins);
        }
    }

    /**
     * Mocks methods which must work although we removed all references to CMMN.
     */
    protected void mockUnsupportedCmmnMethods() {
        repositoryService = new RepositoryServiceImpl() {
            @Override
            public CaseDefinitionQuery createCaseDefinitionQuery() {
                return new CaseDefinitionQueryImpl(commandExecutor) {
                    @Override
                    public long executeCount(CommandContext commandContext) {
                        // This method is called by the Cockpit's start page
                        return 0;
                    }

                    @Override
                    public List<CaseDefinition> executeList(CommandContext commandContext, Page page) {
                        return emptyList();
                    }
                };
            }
        };
        caseService = new CaseServiceImpl() {
            @Override
            public CaseInstanceQuery createCaseInstanceQuery() {
                return new CaseInstanceQueryImpl(commandExecutor) {
                    @Override
                    public long executeCount(CommandContext commandContext) {
                        return 0;
                    }

                    @Override
                    public List<CaseInstance> executeList(CommandContext commandContext, Page page) {
                        return emptyList();
                    }
                };
            }

            @Override
            public CaseExecutionQuery createCaseExecutionQuery() {
                return new CaseExecutionQueryImpl(commandExecutor) {
                    @Override
                    public long executeCount(CommandContext commandContext) {
                        return 0;
                    }

                    @Override
                    public List<CaseExecution> executeList(CommandContext commandContext, Page page) {
                        return emptyList();
                    }
                };
            }
        };
        historyService = new HistoryServiceImpl() {
            @Override
            public HistoricCaseInstanceQuery createHistoricCaseInstanceQuery() {
                return new HistoricCaseInstanceQueryImpl(commandExecutor) {
                    @Override
                    public long executeCount(CommandContext commandContext) {
                        return 0;
                    }

                    @Override
                    public List<HistoricCaseInstance> executeList(CommandContext commandContext, Page page) {
                        return emptyList();
                    }
                };
            }

            @Override
            public HistoricCaseActivityInstanceQuery createHistoricCaseActivityInstanceQuery() {
                return new org.camunda.bpm.engine.impl.HistoricCaseActivityInstanceQueryImpl(commandExecutor) {
                    @Override
                    public long executeCount(CommandContext commandContext) {
                        return 0;
                    }

                    @Override
                    public List<HistoricCaseActivityInstance> executeList(CommandContext commandContext, Page page) {
                        return emptyList();
                    }
                };
            }

            @Override
            public HistoricCaseActivityStatisticsQuery createHistoricCaseActivityStatisticsQuery(String caseDefinitionId) {
                return new HistoricCaseActivityStatisticsQueryImpl(caseDefinitionId, commandExecutor) {
                    @Override
                    public long executeCount(CommandContext commandContext) {
                        return 0;
                    }

                    @Override
                    public List<HistoricCaseActivityStatistics> executeList(CommandContext commandContext, Page page) {
                        return emptyList();
                    }
                };
            }

            @Override
            public CleanableHistoricCaseInstanceReport createCleanableHistoricCaseInstanceReport() {
                return new CleanableHistoricCaseInstanceReportImpl(commandExecutor) {
                    @Override
                    public long executeCount(CommandContext commandContext) {
                        return 0;
                    }

                    @Override
                    public List<CleanableHistoricCaseInstanceReportResult> executeList(CommandContext commandContext, Page page) {
                        return emptyList();
                    }
                };
            }
        };
    }

    @Override
    protected InputStream getMyBatisXmlConfigurationSteam() {
        if (engineConfig.twoStage()) {
            return getMyBatisXmlConfigurationSteamStageOne();
        } else {
            BufferedReader reader = new BufferedReader(new InputStreamReader(super.getMyBatisXmlConfigurationSteam()));
            try {
                StringBuilder sb = new StringBuilder();
                while (reader.ready()) {
                    String line = reader.readLine();
                    if (!line.contains("<mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/Case")) {
                        sb.append(line);
                        sb.append("\n");
                    } else {
                        logger.debug("Filtered out CMMN mapping {}", line);
                    }
                }
                return new ByteArrayInputStream(sb.toString().getBytes());
            } catch (IOException e) {
                throw new RuntimeException("Failed to read MyBatis mappings.xml", e);
            }
        }
    }

    protected InputStream getMyBatisXmlConfigurationSteamStageOne() {
        String s = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<!DOCTYPE configuration PUBLIC \"-//mybatis.org//DTD Config 3.0//EN\" \"http://mybatis.org/dtd/mybatis-3-config.dtd\">\n" +
            "\n" +
            "<configuration>\n" +
            "\t<settings>\n" +
            "\t\t<setting name=\"lazyLoadingEnabled\" value=\"false\" />\n" +
            "\t</settings>\n" +
            "\t<mappers>\n" +
            "        <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/Commons.xml\" />\n" +
            "        <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/Authorization.xml\" />\n" +
            "        <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/Tenant.xml\" />\n" +

            "        <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/Deployment.xml\" />\n" +
            "        <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/Incident.xml\" />\n" + // e.g. New process definition is deployed which replaces a previous version that included a Timer Start Event
            "        <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/Job.xml\" />\n" +
            "        <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/JobDefinition.xml\" />\n" +
            "        <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/ProcessDefinition.xml\" />\n" +
            "        <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/Property.xml\" />\n" +
            "        <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/Resource.xml\" />\n" +
            "        <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/Task.xml\" />\n" +
            "        <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/EventSubscription.xml\" />\n" + // e.g. Message Start Events are registered during deployment
            "        <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/Filter.xml\" />\n" + // FilterAllTasksCreator
            "        <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/User.xml\" />\n" + //AdminUserCreator
            "        <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/Group.xml\" />\n" + //AdminUserCreator
            "        <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/Membership.xml\" />\n" + //AdminUserCreator
            "        <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/CamundaFormDefinition.xml\" />\n" + //Deployment BPMN
            "    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/DecisionDefinition.xml\" />\n" + //Deployment DMN
            "    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/DecisionRequirementsDefinition.xml\" />" + // Deployment DMN
            (getHistoryLevel() == HISTORY_LEVEL_FULL ? "    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/HistoricJobLog.xml\" />\n" : "") + // full history
            "\n" +
            "\t</mappers>\n" +
            "</configuration>\n";
        return new ByteArrayInputStream(s.getBytes());
    }

    public org.apache.ibatis.session.Configuration createConfigurationStageTwo() {
        // This code is based on {@link ProcessEngineConfigurationImpl::initSqlSessionFactory}
        Reader reader = new InputStreamReader(getMyBatisXmlConfigurationSteamStageTwo());
        Properties properties = new Properties();
        if (isUseSharedSqlSessionFactory) {
            properties.put("prefix", "${@org.camunda.bpm.engine.impl.context.Context@getProcessEngineConfiguration().databaseTablePrefix}");
        } else {
            properties.put("prefix", databaseTablePrefix);
        }
        initSqlSessionFactoryProperties(properties, databaseTablePrefix, databaseType);

        XMLConfigBuilder parser = new XMLConfigBuilder(reader, "", properties);
        org.apache.ibatis.session.Configuration parserConfiguration = parser.getConfiguration();

        // Add existing sql fragments from stage 1 in case they are referenced in stage 2
        parserConfiguration.getSqlFragments().putAll(getSqlSessionFactory().getConfiguration().getSqlFragments());
        parser.parse();
        return parserConfiguration;
    }

    protected InputStream getMyBatisXmlConfigurationSteamStageTwo() {
        String s = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<!DOCTYPE configuration PUBLIC \"-//mybatis.org//DTD Config 3.0//EN\" \"http://mybatis.org/dtd/mybatis-3-config.dtd\">\n" +
            "<configuration>\n" +
            "\t<settings>\n" +
            "\t\t<setting name=\"lazyLoadingEnabled\" value=\"false\" />\n" +
            "\t</settings>\n" +
            "\t<mappers>\n" +
            "    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/Report.xml\" />\n" +

            "    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/Attachment.xml\" />\n" +
            "    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/Comment.xml\" />\n" +
            //"    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/Deployment.xml\" />\n" +
            "    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/Execution.xml\" />\n" +
            //"    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/Group.xml\" />\n" +
            "    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/HistoricActivityInstance.xml\" />\n" +
            "    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/HistoricCaseActivityInstance.xml\" />\n" +
            "    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/HistoricDetail.xml\" />\n" +
            "    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/HistoricIncident.xml\" />\n" +
            "    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/HistoricIdentityLinkLog.xml\" />\n" +
            "    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/HistoricProcessInstance.xml\" />\n" +
            "    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/HistoricCaseInstance.xml\" />\n" +
            "    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/HistoricStatistics.xml\" />\n" +
            "    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/HistoricVariableInstance.xml\" />\n" +
            "    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/HistoricTaskInstance.xml\" />\n" +
            "    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/HistoricTaskInstanceReport.xml\" />\n" +
            (getHistoryLevel() != HISTORY_LEVEL_FULL ? "    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/HistoricJobLog.xml\" />\n" : "") +
            "    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/HistoricExternalTaskLog.xml\" />\n" +
            "    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/UserOperationLogEntry.xml\" />\n" +
            "    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/IdentityInfo.xml\" />\n" +
            "    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/IdentityLink.xml\" />\n" +
            //"    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/Job.xml\" />\n" +
            //"    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/JobDefinition.xml\" />\n" +
            //"    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/Incident.xml\" />\n" +
            //"    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/Membership.xml\" />\n" +
            //"    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/ProcessDefinition.xml\" />\n" +
            //"    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/Property.xml\" />\n" +
            "    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/SchemaLogEntry.xml\" />\n" +
            //"    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/Resource.xml\" />\n" +
            "    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/TableData.xml\" />\n" +
            "    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/TaskMetrics.xml\" />\n" +
            //"    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/Task.xml\" />\n" +
            //"    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/User.xml\" />\n" +
            "    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/VariableInstance.xml\" />\n" +
            //"    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/EventSubscription.xml\" />\n" +
            "    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/Statistics.xml\" />\n" +
            //"    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/Filter.xml\" />\n" +
            "    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/Metrics.xml\" />\n" +
            "    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/ExternalTask.xml\" />\n" +
            "    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/Batch.xml\" />\n" +
            "    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/HistoricBatch.xml\" />\n" +
            "    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/TenantMembership.xml\" />\n" +
            //"    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/CamundaFormDefinition.xml\" />\n" +

            // Never include CMMN - not supported
            //"    <!-- CMMN -->\n" +
            //"\n" +
            //"    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/CaseDefinition.xml\" />\n" +
            //"    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/CaseExecution.xml\" />\n" +
            //"    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/CaseSentryPart.xml\" />\n" +

            "    <!-- DMN -->\n" +
            //"    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/DecisionDefinition.xml\" />\n" +
            "    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/HistoricDecisionInstance.xml\" />\n" +
            "    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/HistoricDecisionInputInstance.xml\" />\n" +
            "    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/HistoricDecisionOutputInstance.xml\" />\n" +
            //"    <mapper resource=\"org/camunda/bpm/engine/impl/mapping/entity/DecisionRequirementsDefinition.xml\" />" +
            "\t</mappers>\n" +
            "</configuration>\n";
        return new ByteArrayInputStream(s.getBytes());
    }
}
