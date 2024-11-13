package ru.tinkoff.kora.camunda.engine.bpmn.configurator;

import org.apache.ibatis.session.Configuration;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.jobexecutor.JobExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.camunda.engine.bpmn.CamundaEngineBpmnConfig;
import ru.tinkoff.kora.camunda.engine.bpmn.KoraProcessEngine;
import ru.tinkoff.kora.camunda.engine.bpmn.KoraProcessEngineConfiguration;
import ru.tinkoff.kora.common.util.TimeUtils;

import java.util.concurrent.atomic.AtomicInteger;

public final class SecondStageKoraProcessEngineConfigurator implements ProcessEngineConfigurator {

    private static final Logger logger = LoggerFactory.getLogger(KoraProcessEngine.class);

    private final ProcessEngineConfiguration engineConfiguration;
    private final CamundaEngineBpmnConfig engineConfig;
    private final JobExecutor jobExecutor;

    public SecondStageKoraProcessEngineConfigurator(ProcessEngineConfiguration engineConfiguration,
                                                    CamundaEngineBpmnConfig engineConfig,
                                                    JobExecutor jobExecutor) {
        this.engineConfiguration = engineConfiguration;
        this.engineConfig = engineConfig;
        this.jobExecutor = jobExecutor;
    }

    @Override
    public void setup(ProcessEngine engine) {
        if (engineConfig.parallelInitialization().enabled() && engineConfiguration instanceof KoraProcessEngineConfiguration kEngine) {
            logger.debug("Camunda BPMN Engine starting second stage...");
            final long started = TimeUtils.started();

            Configuration src = kEngine.createConfigurationStageTwo();
            Configuration dest = kEngine.getSqlSessionFactory().getConfiguration();

            final AtomicInteger statements = new AtomicInteger(0);
            src.getMappedStatements().forEach(ms -> {
                if (!dest.hasStatement(ms.getId(), engineConfig.parallelInitialization().validateIncompleteStatements())) {
                    dest.addMappedStatement(ms);
                    statements.incrementAndGet();
                }
            });
            if (jobExecutor.isAutoActivate()) {
                jobExecutor.start();
            }

            logger.info("Camunda BPMN Engine started second stage with {} new and total {} mapped statements in {}",
                statements.get(), dest.getMappedStatements().size(), TimeUtils.tookForLogging(started));
        }
    }
}
