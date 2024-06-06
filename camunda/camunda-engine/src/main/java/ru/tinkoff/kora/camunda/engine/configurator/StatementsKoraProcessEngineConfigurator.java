package ru.tinkoff.kora.camunda.engine.configurator;

import org.apache.ibatis.session.Configuration;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.jobexecutor.JobExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.camunda.engine.CamundaEngineConfig;
import ru.tinkoff.kora.camunda.engine.KoraProcessEngineConfiguration;
import ru.tinkoff.kora.common.util.TimeUtils;

import java.util.concurrent.atomic.AtomicInteger;

public final class StatementsKoraProcessEngineConfigurator implements ProcessEngineConfigurator {

    private static final Logger logger = LoggerFactory.getLogger(StatementsKoraProcessEngineConfigurator.class);

    private final ProcessEngineConfiguration processEngineConfiguration;
    private final CamundaEngineConfig engineConfig;
    private final JobExecutor jobExecutor;

    public StatementsKoraProcessEngineConfigurator(ProcessEngineConfiguration processEngineConfiguration,
                                                   CamundaEngineConfig engineConfig,
                                                   JobExecutor jobExecutor) {
        this.processEngineConfiguration = processEngineConfiguration;
        this.engineConfig = engineConfig;
        this.jobExecutor = jobExecutor;
    }

    @Override
    public void setup(ProcessEngine engine) {
        if (engineConfig.initializeParallel()) {
            if (processEngineConfiguration instanceof KoraProcessEngineConfiguration kEngine) {
                logger.debug("Camunda Configurator processing required mapped statements...");
                final long started = TimeUtils.started();

                Configuration src = kEngine.createConfigurationStageTwo();
                Configuration dest = kEngine.getSqlSessionFactory().getConfiguration();

                final AtomicInteger statements = new AtomicInteger(0);
                src.getMappedStatements().forEach(ms -> {
                    //TODO check if validateIncompleteStatements=false can be applied
                    if (!dest.hasStatement(ms.getId())) {
                        dest.addMappedStatement(ms);
                        statements.incrementAndGet();
                    }
                });
                if (jobExecutor.isAutoActivate()) {
                    jobExecutor.start();
                }

                logger.info("Camunda Configurator processed {} new mapped statements with total {} mapped statements in {}",
                    statements.get(), dest.getMappedStatements().size(), TimeUtils.tookForLogging(started));
            }
        }
    }
}
