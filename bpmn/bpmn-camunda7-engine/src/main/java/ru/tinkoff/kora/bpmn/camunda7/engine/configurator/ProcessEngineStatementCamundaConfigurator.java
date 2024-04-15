package ru.tinkoff.kora.bpmn.camunda7.engine.configurator;

import org.apache.ibatis.session.Configuration;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.jobexecutor.JobExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.bpmn.camunda7.engine.CamundaEngineConfig;
import ru.tinkoff.kora.bpmn.camunda7.engine.KoraProcessEngineConfiguration;

import java.util.concurrent.atomic.AtomicInteger;

public final class ProcessEngineStatementCamundaConfigurator implements CamundaConfigurator {

    private static final Logger logger = LoggerFactory.getLogger(ProcessEngineStatementCamundaConfigurator.class);

    private final KoraProcessEngineConfiguration processEngineConfiguration;
    private final CamundaEngineConfig engineConfig;
    private final JobExecutor jobExecutor;

    public ProcessEngineStatementCamundaConfigurator(KoraProcessEngineConfiguration processEngineConfiguration,
                                                     CamundaEngineConfig engineConfig,
                                                     JobExecutor jobExecutor) {
        this.processEngineConfiguration = processEngineConfiguration;
        this.engineConfig = engineConfig;
        this.jobExecutor = jobExecutor;
    }

    @Override
    public void setup(ProcessEngine processEngine) throws Exception {
        if (engineConfig.twoStage()) {
            Configuration src = processEngineConfiguration.createConfigurationStageTwo();
            Configuration dest = processEngineConfiguration.getSqlSessionFactory().getConfiguration();

            final AtomicInteger statements = new AtomicInteger(0);
            src.getMappedStatements().forEach(ms -> {
                if (!dest.hasStatement(ms.getId())) {
                    dest.addMappedStatement(ms);
                    statements.incrementAndGet();
                }
            });
            logger.debug("Copied {} mapped statements. New total is {} mapped statements.", statements.get(), dest.getMappedStatements().size());

            if (jobExecutor.isAutoActivate()) {
                jobExecutor.start();
            }
        }
    }
}
