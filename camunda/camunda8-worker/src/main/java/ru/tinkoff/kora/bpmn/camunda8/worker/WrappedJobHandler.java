package ru.tinkoff.kora.bpmn.camunda8.worker;

import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.command.ThrowErrorCommandStep1.ThrowErrorCommandStep2;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobHandler;
import ru.tinkoff.kora.bpmn.camunda8.worker.telemetry.Camunda8WorkerTelemetry;

final class WrappedJobHandler implements JobHandler {

    private final Camunda8WorkerTelemetry telemetry;
    private final KoraJobWorker jobHandler;

    public WrappedJobHandler(Camunda8WorkerTelemetry telemetry, KoraJobWorker jobHandler) {
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
                    telemetryContext.close(Camunda8WorkerTelemetry.ErrorType.SYSTEM, e);
                } else {
                    telemetryContext.close();
                }
            });
        } catch (Exception e) {
            JobWorkerException jonException;
            if (e instanceof JobWorkerException ex) {
                jonException = ex;
            } else {
                jonException = new JobWorkerException("INTERNAL", e);
            }

            var command = createErrorCommand(client, job, jonException);
            command.send().whenComplete((r, ex) -> {
                if (ex != null) {
                    telemetryContext.close(Camunda8WorkerTelemetry.ErrorType.SYSTEM, e);
                } else {
                    telemetryContext.close(Camunda8WorkerTelemetry.ErrorType.USER, e);
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
