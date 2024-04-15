package ru.tinkoff.kora.bpmn.camunda7.engine;

import jakarta.annotation.Nullable;
import org.camunda.bpm.engine.impl.jobexecutor.JobExecutor;
import ru.tinkoff.kora.common.readiness.ReadinessProbe;
import ru.tinkoff.kora.common.readiness.ReadinessProbeFailure;

public final class CamundaReadinessProbe implements ReadinessProbe {

    private final JobExecutor jobExecutor;

    public CamundaReadinessProbe(JobExecutor jobExecutor) {
        this.jobExecutor = jobExecutor;
    }

    @Nullable
    @Override
    public ReadinessProbeFailure probe() throws Exception {
        if (jobExecutor.isAutoActivate()) {
            return null;
        } else {
            return new ReadinessProbeFailure("Camunda Engine JobExecutor is not active");
        }
    }
}
