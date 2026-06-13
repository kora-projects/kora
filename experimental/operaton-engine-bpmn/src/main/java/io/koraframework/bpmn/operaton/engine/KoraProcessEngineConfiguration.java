package io.koraframework.bpmn.operaton.engine;

import io.koraframework.bpmn.operaton.engine.transaction.KoraTransactionContextFactory;
import io.koraframework.bpmn.operaton.engine.transaction.KoraTransactionInterceptor;
import org.apache.ibatis.builder.xml.XMLConfigBuilder;
import org.operaton.bpm.engine.ArtifactFactory;
import org.operaton.bpm.engine.ProcessEngineConfiguration;
import org.operaton.bpm.engine.history.*;
import org.operaton.bpm.engine.impl.*;
import org.operaton.bpm.engine.impl.cfg.IdGenerator;
import org.operaton.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.operaton.bpm.engine.impl.cfg.ProcessEnginePlugin;
import org.operaton.bpm.engine.impl.cmmn.CaseServiceImpl;
import org.operaton.bpm.engine.impl.cmmn.entity.repository.CaseDefinitionQueryImpl;
import org.operaton.bpm.engine.impl.cmmn.entity.runtime.CaseExecutionQueryImpl;
import org.operaton.bpm.engine.impl.cmmn.entity.runtime.CaseInstanceQueryImpl;
import org.operaton.bpm.engine.impl.el.JuelExpressionManager;
import org.operaton.bpm.engine.impl.interceptor.*;
import org.operaton.bpm.engine.impl.jobexecutor.JobExecutor;
import org.operaton.bpm.engine.repository.CaseDefinition;
import org.operaton.bpm.engine.repository.CaseDefinitionQuery;
import org.operaton.bpm.engine.runtime.CaseExecution;
import org.operaton.bpm.engine.runtime.CaseExecutionQuery;
import org.operaton.bpm.engine.runtime.CaseInstance;
import org.operaton.bpm.engine.runtime.CaseInstanceQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static java.util.Collections.emptyList;
import static org.operaton.bpm.engine.impl.history.HistoryLevel.HISTORY_LEVEL_FULL;

public class KoraProcessEngineConfiguration extends ProcessEngineConfigurationImpl {

    private static final Logger logger = LoggerFactory.getLogger(KoraProcessEngineConfiguration.class);

    private final JobExecutor jobExecutor;
    private final KoraResolverFactory componentResolverFactory;
    private final OperatonEngineDataSource engineDataSource;
    private final OperatonEngineBpmnConfig engineConfig;
    private final Iterable<ProcessEnginePlugin> plugins;

    public KoraProcessEngineConfiguration(JobExecutor jobExecutor,
                                          IdGenerator idGenerator,
                                          JuelExpressionManager koraExpressionManager,
                                          ArtifactFactory artifactFactory,
                                          Iterable<ProcessEnginePlugin> plugins,
                                          OperatonEngineDataSource engineDataSource,
                                          OperatonEngineBpmnConfig engineConfig,
                                          KoraResolverFactory componentResolverFactory) {
        this.jobExecutor = jobExecutor;
        this.componentResolverFactory = componentResolverFactory;
        this.idGenerator = idGenerator;
        this.artifactFactory = artifactFactory;
        this.plugins = plugins;
        this.engineConfig = engineConfig;
        this.engineDataSource = engineDataSource;

        setDefaultCharset(StandardCharsets.UTF_8);

        setDataSource(engineDataSource.dataSource());
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
            transactionContextFactory = new KoraTransactionContextFactory(engineDataSource.transactionManager());
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
    protected Collection<CommandInterceptor> getDefaultCommandInterceptorsTxRequired() {
        return getCommandInterceptors(false);
    }

    @Override
    protected Collection<CommandInterceptor> getDefaultCommandInterceptorsTxRequiresNew() {
        return getCommandInterceptors(true);
    }

    protected List<CommandInterceptor> getCommandInterceptors(boolean requiresNew) {
        return Arrays.asList(
            new LogInterceptor(),
            new CommandCounterInterceptor(this),
            new ProcessApplicationContextInterceptor(this),
            new KoraTransactionInterceptor(engineDataSource.transactionManager(), requiresNew),
            new CommandContextInterceptor(commandContextFactory, this, requiresNew)
        );
    }

    protected void configureMetricsAndTelemetry() {
        if (engineConfig.telemetry().engineTelemetryEnabled()) {
            setMetricsEnabled(true);
            setTaskMetricsEnabled(true);
        } else {
            setMetricsEnabled(false);
            setTaskMetricsEnabled(false);
        }
    }

    protected void configureDefaultValues() {
        setJobExecutorActivate(true);
        setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        setEnforceHistoryTimeToLive(false);
    }

    protected void registerProcessEnginePlugins() {
        var pluginsList = new ArrayList<ProcessEnginePlugin>();
        for (var plugin : plugins) {
            pluginsList.add(plugin);
        }


        if (!pluginsList.isEmpty()) {
            logger.info("Registering process engine plugins: {}", plugins);
            setProcessEnginePlugins(pluginsList);
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
                return new org.operaton.bpm.engine.impl.HistoricCaseActivityInstanceQueryImpl(commandExecutor) {
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
        if (engineConfig.parallelInitialization().enabled()) {
            return getMyBatisXmlConfigurationSteamStageOne();
        } else {
            BufferedReader reader = new BufferedReader(new InputStreamReader(super.getMyBatisXmlConfigurationSteam()));
            try {
                StringBuilder sb = new StringBuilder();
                while (reader.ready()) {
                    String line = reader.readLine();
                    if (!line.contains("<mapper resource=\"org/operaton/bpm/engine/impl/mapping/entity/Case")) {
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
            "\n<configuration>\n" +
            "\t<settings>\n" +
            "\t\t<setting name=\"lazyLoadingEnabled\" value=\"false\" />\n" +
            "\t</settings>\n" +
            "\t<mappers>\n" +
            "    <mapper resource=\"org/operaton/bpm/engine/impl/mapping/entity/Commons.xml\" />\n" +
            "    <mapper resource=\"org/operaton/bpm/engine/impl/mapping/entity/Authorization.xml\" />\n" +
            "    <mapper resource=\"org/operaton/bpm/engine/impl/mapping/entity/Tenant.xml\" />\n" +
            "    <mapper resource=\"org/operaton/bpm/engine/impl/mapping/entity/Deployment.xml\" />\n" +
            "    <mapper resource=\"org/operaton/bpm/engine/impl/mapping/entity/Incident.xml\" />\n" + // e.g. New process definition is deployed which replaces a previous version that included a Timer Start Event
            "    <mapper resource=\"org/operaton/bpm/engine/impl/mapping/entity/Job.xml\" />\n" +
            "    <mapper resource=\"org/operaton/bpm/engine/impl/mapping/entity/JobDefinition.xml\" />\n" +
            "    <mapper resource=\"org/operaton/bpm/engine/impl/mapping/entity/ProcessDefinition.xml\" />\n" +
            "    <mapper resource=\"org/operaton/bpm/engine/impl/mapping/entity/Property.xml\" />\n" +
            "    <mapper resource=\"org/operaton/bpm/engine/impl/mapping/entity/Resource.xml\" />\n" +
            "    <mapper resource=\"org/operaton/bpm/engine/impl/mapping/entity/Task.xml\" />\n" +
            "    <mapper resource=\"org/operaton/bpm/engine/impl/mapping/entity/EventSubscription.xml\" />\n" + // e.g. Message Start Events are registered during deployment
            "    <mapper resource=\"org/operaton/bpm/engine/impl/mapping/entity/Filter.xml\" />\n" + // FilterAllTasksCreator
            "    <mapper resource=\"org/operaton/bpm/engine/impl/mapping/entity/TaskMetrics.xml\" />\n" + // Engine metrics can be flushed before stage two
            "    <mapper resource=\"org/operaton/bpm/engine/impl/mapping/entity/Metrics.xml\" />\n" + // DbMetricsReporter can flush on stage one close
            "    <mapper resource=\"org/operaton/bpm/engine/impl/mapping/entity/User.xml\" />\n" + //AdminUserCreator
            "    <mapper resource=\"org/operaton/bpm/engine/impl/mapping/entity/Group.xml\" />\n" + //AdminUserCreator
            "    <mapper resource=\"org/operaton/bpm/engine/impl/mapping/entity/Membership.xml\" />\n" + //AdminUserCreator
            "    <mapper resource=\"org/operaton/bpm/engine/impl/mapping/entity/OperatonFormDefinition.xml\" />\n" + //Deployment BPMN
            "    <mapper resource=\"org/operaton/bpm/engine/impl/mapping/entity/DecisionDefinition.xml\" />\n" + //Deployment DMN
            "    <mapper resource=\"org/operaton/bpm/engine/impl/mapping/entity/DecisionRequirementsDefinition.xml\" />" + // Deployment DMN
            "\n%s".formatted((getHistoryLevel() == HISTORY_LEVEL_FULL ? "    <mapper resource=\"org/operaton/bpm/engine/impl/mapping/entity/HistoricJobLog.xml\" />\n" : "")) +
            "\t</mappers>\n" +
            "</configuration>\n";
        return new ByteArrayInputStream(s.getBytes());
    }

    public org.apache.ibatis.session.Configuration createConfigurationStageTwo() {
        // This code is based on {@link ProcessEngineConfigurationImpl::initSqlSessionFactory}
        Reader reader = new InputStreamReader(getMyBatisXmlConfigurationSteamStageTwo());
        Properties properties = new Properties();
        if (isUseSharedSqlSessionFactory) {
            properties.put("prefix", "${@org.operaton.bpm.engine.impl.context.Context@getProcessEngineConfiguration().databaseTablePrefix}");
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
        String s = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE configuration PUBLIC "-//mybatis.org//DTD Config 3.0//EN" "http://mybatis.org/dtd/mybatis-3-config.dtd">
            <configuration>
            \t<settings>
            \t\t<setting name="lazyLoadingEnabled" value="false" />
            \t</settings>
            \t<mappers>
                <mapper resource="org/operaton/bpm/engine/impl/mapping/entity/Report.xml" />
                <mapper resource="org/operaton/bpm/engine/impl/mapping/entity/Attachment.xml" />
                <mapper resource="org/operaton/bpm/engine/impl/mapping/entity/Comment.xml" />
                <mapper resource="org/operaton/bpm/engine/impl/mapping/entity/Execution.xml" />
                <mapper resource="org/operaton/bpm/engine/impl/mapping/entity/HistoricActivityInstance.xml" />
                <mapper resource="org/operaton/bpm/engine/impl/mapping/entity/HistoricCaseActivityInstance.xml" />
                <mapper resource="org/operaton/bpm/engine/impl/mapping/entity/HistoricDetail.xml" />
                <mapper resource="org/operaton/bpm/engine/impl/mapping/entity/HistoricIncident.xml" />
                <mapper resource="org/operaton/bpm/engine/impl/mapping/entity/HistoricIdentityLinkLog.xml" />
                <mapper resource="org/operaton/bpm/engine/impl/mapping/entity/HistoricProcessInstance.xml" />
                <mapper resource="org/operaton/bpm/engine/impl/mapping/entity/HistoricCaseInstance.xml" />
                <mapper resource="org/operaton/bpm/engine/impl/mapping/entity/HistoricStatistics.xml" />
                <mapper resource="org/operaton/bpm/engine/impl/mapping/entity/HistoricVariableInstance.xml" />
                <mapper resource="org/operaton/bpm/engine/impl/mapping/entity/HistoricTaskInstance.xml" />
                <mapper resource="org/operaton/bpm/engine/impl/mapping/entity/HistoricTaskInstanceReport.xml" />%s
                <mapper resource="org/operaton/bpm/engine/impl/mapping/entity/HistoricExternalTaskLog.xml" />
                <mapper resource="org/operaton/bpm/engine/impl/mapping/entity/UserOperationLogEntry.xml" />
                <mapper resource="org/operaton/bpm/engine/impl/mapping/entity/IdentityInfo.xml" />
                <mapper resource="org/operaton/bpm/engine/impl/mapping/entity/IdentityLink.xml" />
                <mapper resource="org/operaton/bpm/engine/impl/mapping/entity/SchemaLogEntry.xml" />
                <mapper resource="org/operaton/bpm/engine/impl/mapping/entity/TableData.xml" />
                <mapper resource="org/operaton/bpm/engine/impl/mapping/entity/VariableInstance.xml" />
                <mapper resource="org/operaton/bpm/engine/impl/mapping/entity/Statistics.xml" />
                <mapper resource="org/operaton/bpm/engine/impl/mapping/entity/ExternalTask.xml" />
                <mapper resource="org/operaton/bpm/engine/impl/mapping/entity/Batch.xml" />
                <mapper resource="org/operaton/bpm/engine/impl/mapping/entity/HistoricBatch.xml" />
                <mapper resource="org/operaton/bpm/engine/impl/mapping/entity/TenantMembership.xml" />
                <!-- DMN -->
                <mapper resource="org/operaton/bpm/engine/impl/mapping/entity/HistoricDecisionInstance.xml" />
                <mapper resource="org/operaton/bpm/engine/impl/mapping/entity/HistoricDecisionInputInstance.xml" />
                <mapper resource="org/operaton/bpm/engine/impl/mapping/entity/HistoricDecisionOutputInstance.xml" />
            \t</mappers>
            </configuration>
            """.formatted(getHistoryLevel() != HISTORY_LEVEL_FULL ? "\n    <mapper resource=\"org/operaton/bpm/engine/impl/mapping/entity/HistoricJobLog.xml\" />\n" : "");
        return new ByteArrayInputStream(s.getBytes());
    }
}
