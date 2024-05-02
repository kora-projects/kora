package ru.tinkoff.kora.camunda.zeebe.worker;

import io.camunda.zeebe.client.api.response.ActivatedJob;

import java.time.Instant;
import java.util.Map;

public record ActiveJobContext(String name, ActivatedJob job) implements JobContext {

    @Override
    public long jobKey() {
        return job.getKey();
    }

    @Override
    public String jobName() {
        return name;
    }

    @Override
    public String jobType() {
        return job.getType();
    }

    @Override
    public String jobWorker() {
        return job.getWorker();
    }

    @Override
    public String tenantId() {
        return job.getTenantId();
    }

    @Override
    public long processInstanceKey() {
        return job.getProcessInstanceKey();
    }

    @Override
    public String processId() {
        return job.getBpmnProcessId();
    }

    @Override
    public int processDefinitionVersion() {
        return job.getProcessDefinitionVersion();
    }

    @Override
    public long processDefinitionKey() {
        return job.getProcessDefinitionKey();
    }

    @Override
    public String elementId() {
        return job.getElementId();
    }

    @Override
    public long elementInstanceKey() {
        return job.getElementInstanceKey();
    }

    @Override
    public Map<String, String> headers() {
        return job.getCustomHeaders();
    }

    @Override
    public int retryCount() {
        return job.getRetries();
    }

    @Override
    public Instant deadline() {
        return Instant.ofEpochMilli(job.getDeadline());
    }

    @Override
    public long deadlineAsMillis() {
        return job.getDeadline();
    }

    @Override
    public String variablesAsString() {
        return job.getVariables();
    }

    @Override
    public String toString() {
        return "[jobName=" + jobName() + ", jobType=" + jobType() + ", jobKey=" + jobKey() + ", processId=" + processId() + ", elementId=" + elementId() + ']';
    }
}
