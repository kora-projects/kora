package ru.tinkoff.kora.bpmn.camunda8.worker;

import io.camunda.zeebe.client.api.response.ActivatedJob;

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
    public String toString() {
        return "[jobName=" + jobName() + ", jobType=" + jobType() + ", processId=" + processId() + ", elementId=" + elementId() + ']';
    }
}
