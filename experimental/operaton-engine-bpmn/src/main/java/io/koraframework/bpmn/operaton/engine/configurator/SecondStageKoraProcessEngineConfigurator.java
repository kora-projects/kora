package io.koraframework.bpmn.operaton.engine.configurator;

import org.apache.ibatis.session.Configuration;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.ProcessEngineConfiguration;
import org.operaton.bpm.engine.impl.jobexecutor.JobExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.koraframework.bpmn.operaton.engine.OperatonEngineBpmnConfig;
import io.koraframework.bpmn.operaton.engine.KoraProcessEngine;
import io.koraframework.bpmn.operaton.engine.KoraProcessEngineConfiguration;
import io.koraframework.common.util.TimeUtils;

import java.util.concurrent.atomic.AtomicInteger;

public final class SecondStageKoraProcessEngineConfigurator implements ProcessEngineConfigurator {

    private static final Logger logger = LoggerFactory.getLogger(KoraProcessEngine.class);

    private final ProcessEngineConfiguration engineConfiguration;
    private final OperatonEngineBpmnConfig engineConfig;
    private final JobExecutor jobExecutor;

    public SecondStageKoraProcessEngineConfigurator(ProcessEngineConfiguration engineConfiguration,
                                                    OperatonEngineBpmnConfig engineConfig,
                                                    JobExecutor jobExecutor) {
        this.engineConfiguration = engineConfiguration;
        this.engineConfig = engineConfig;
        this.jobExecutor = jobExecutor;
    }

    @Override
    public void setup(ProcessEngine engine) {
        if (engineConfig.parallelInitialization().enabled() && engineConfiguration instanceof KoraProcessEngineConfiguration kEngine) {
            logger.debug("Operaton BPMN Engine starting second stage...");
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

            logger.info("Operaton BPMN Engine started second stage with {} new and total {} mapped statements in {}",
                statements.get(), dest.getMappedStatements().size(), TimeUtils.tookForLogging(started));
        }
    }
}
