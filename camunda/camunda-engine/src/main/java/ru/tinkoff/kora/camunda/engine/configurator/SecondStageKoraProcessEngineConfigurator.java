package ru.tinkoff.kora.camunda.engine.configurator;

import org.apache.ibatis.session.Configuration;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.jobexecutor.JobExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.camunda.engine.CamundaEngineConfig;
import ru.tinkoff.kora.camunda.engine.KoraProcessEngine;
import ru.tinkoff.kora.camunda.engine.KoraProcessEngineConfiguration;
import ru.tinkoff.kora.common.util.TimeUtils;

import java.util.concurrent.atomic.AtomicInteger;

public final class SecondStageKoraProcessEngineConfigurator implements ProcessEngineConfigurator {

    private static final Logger logger = LoggerFactory.getLogger(KoraProcessEngine.class);

    private final ProcessEngineConfiguration engineConfiguration;
    private final CamundaEngineConfig engineConfig;
    private final JobExecutor jobExecutor;

    public SecondStageKoraProcessEngineConfigurator(ProcessEngineConfiguration engineConfiguration,
                                                    CamundaEngineConfig engineConfig,
                                                    JobExecutor jobExecutor) {
        this.engineConfiguration = engineConfiguration;
        this.engineConfig = engineConfig;
        this.jobExecutor = jobExecutor;
    }

    @Override
    public void setup(ProcessEngine engine) {
        if (engineConfig.parallelInitialization().enabled() && engineConfiguration instanceof KoraProcessEngineConfiguration kEngine) {
            logger.debug("Camunda Engine starting second stage...");
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

            logger.info("Camunda Engine started second stage with {} new and total {} mapped statements in {}",
                statements.get(), dest.getMappedStatements().size(), TimeUtils.tookForLogging(started));
        }
    }
}
