package ru.tinkoff.kora.camunda.engine.bpmn;

import jakarta.annotation.Nullable;
import org.camunda.bpm.engine.impl.jobexecutor.JobExecutor;
import ru.tinkoff.kora.common.readiness.ReadinessProbe;
import ru.tinkoff.kora.common.readiness.ReadinessProbeFailure;

public final class JobExecutorReadinessProbe implements ReadinessProbe {

    private final JobExecutor jobExecutor;

    public JobExecutorReadinessProbe(JobExecutor jobExecutor) {
        this.jobExecutor = jobExecutor;
    }

    @Nullable
    @Override
    public ReadinessProbeFailure probe() {
        if (jobExecutor.isAutoActivate()) {
            return null;
        } else {
            return new ReadinessProbeFailure("Camunda BPMN Engine JobExecutor is not active");
        }
    }
}
