package ru.tinkoff.kora.camunda.zeebe.worker;

import io.camunda.zeebe.client.api.command.FailJobCommandStep1.FailJobCommandStep2;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.command.ThrowErrorCommandStep1.ThrowErrorCommandStep2;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobHandler;
import ru.tinkoff.kora.camunda.zeebe.worker.telemetry.ZeebeWorkerTelemetry;

final class WrappedJobHandler implements JobHandler {

    private final ZeebeWorkerTelemetry telemetry;
    private final KoraJobWorker jobHandler;

    public WrappedJobHandler(ZeebeWorkerTelemetry telemetry, KoraJobWorker jobHandler) {
        this.telemetry = telemetry;
        this.jobHandler = jobHandler;
    }

    @Override
    public void handle(JobClient client, ActivatedJob job) {
        final JobContext jobContext = new ActiveJobContext(jobHandler.type(), job);
        var telemetryContext = telemetry.get(jobContext);

        try {
            var command = jobHandler.handle(client, job);
            command.send().whenComplete((r, e) -> {
                if (e != null) {
                    telemetryContext.close(ZeebeWorkerTelemetry.ErrorType.SYSTEM, e);
                } else {
                    if (command instanceof ThrowErrorCommandStep2 || command instanceof FailJobCommandStep2) {
                        telemetryContext.close(ZeebeWorkerTelemetry.ErrorType.USER, null);
                    } else {
                        telemetryContext.close();
                    }
                }
            });
        } catch (Exception e) {
            JobWorkerException je;
            if (e instanceof JobWorkerException ex) {
                je = ex;
            } else {
                je = new JobWorkerException("INTERNAL", e);
            }

            var command = createErrorCommand(client, job, je);
            command.send().whenComplete((r, ex) -> {
                if (ex != null) {
                    telemetryContext.close(ZeebeWorkerTelemetry.ErrorType.SYSTEM, je);
                } else {
                    telemetryContext.close(ZeebeWorkerTelemetry.ErrorType.USER, je);
                }
            });
        }
    }

    private FinalCommandStep<Void> createErrorCommand(JobClient client, ActivatedJob job, JobWorkerException exception) {
        ThrowErrorCommandStep2 command = client
            .newThrowErrorCommand(job.getKey())
            .errorCode(exception.getCode())
            .errorMessage(exception.getMessage());

        if (exception.getVariables() != null) {
            command.variables(exception.getVariables());
        }

        return command;
    }
}
