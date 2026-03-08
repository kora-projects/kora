package io.koraframework.camunda.engine.bpmn;

import org.camunda.bpm.engine.impl.jobexecutor.JobExecutor;
import org.jspecify.annotations.Nullable;
import io.koraframework.common.readiness.ReadinessProbe;
import io.koraframework.common.readiness.ReadinessProbeFailure;

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
