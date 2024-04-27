package ru.tinkoff.kora.bpmn.camunda7.engine.configurator;

import org.apache.ibatis.session.Configuration;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.jobexecutor.JobExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.bpmn.camunda7.engine.Camunda7EngineConfig;
import ru.tinkoff.kora.bpmn.camunda7.engine.KoraProcessEngineConfiguration;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public final class StatementsProcessEngineConfigurator implements ProcessEngineConfigurator {

    private static final Logger logger = LoggerFactory.getLogger(StatementsProcessEngineConfigurator.class);

    private final KoraProcessEngineConfiguration processEngineConfiguration;
    private final Camunda7EngineConfig engineConfig;
    private final JobExecutor jobExecutor;

    public StatementsProcessEngineConfigurator(KoraProcessEngineConfiguration processEngineConfiguration,
                                               Camunda7EngineConfig engineConfig,
                                               JobExecutor jobExecutor) {
        this.processEngineConfiguration = processEngineConfiguration;
        this.engineConfig = engineConfig;
        this.jobExecutor = jobExecutor;
    }

    @Override
    public void setup(ProcessEngine engine) throws Exception {
        if (engineConfig.initializeParallel()) {
            logger.debug("Camunda7 Configurator processing required mapped statements...");
            final long started = System.nanoTime();

            Configuration src = processEngineConfiguration.createConfigurationStageTwo();
            Configuration dest = processEngineConfiguration.getSqlSessionFactory().getConfiguration();

            final AtomicInteger statements = new AtomicInteger(0);
            src.getMappedStatements().forEach(ms -> {
                if (!dest.hasStatement(ms.getId())) {
                    dest.addMappedStatement(ms);
                    statements.incrementAndGet();
                }
            });
            if (jobExecutor.isAutoActivate()) {
                jobExecutor.start();
            }

            logger.info("Camunda7 Configurator processed {} new mapped statements with total {} mapped statements in {}",
                statements.get(), dest.getMappedStatements().size(), Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
        }
    }
}
