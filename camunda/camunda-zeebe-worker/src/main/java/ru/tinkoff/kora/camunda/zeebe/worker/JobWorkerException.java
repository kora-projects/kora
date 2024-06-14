package ru.tinkoff.kora.camunda.zeebe.worker;

import jakarta.annotation.Nullable;

import java.util.Map;

public class JobWorkerException extends RuntimeException {

    private final String code;
    private final Map<String, Object> variables;

    public JobWorkerException(String code) {
        super("Failed with code: " + code);
        this.code = code;
        this.variables = null;
    }

    public JobWorkerException(String code, @Nullable Map<String, Object> variables) {
        super("Failed with code: " + code);
        this.code = code;
        this.variables = variables;
    }

    public JobWorkerException(String code, Throwable cause) {
        super(cause.getMessage(), cause);
        this.code = code;
        this.variables = null;
    }

    public JobWorkerException(String code, String cause) {
        super(cause);
        this.code = code;
        this.variables = null;
    }

    public JobWorkerException(String code, Throwable cause, @Nullable Map<String, Object> variables) {
        super(cause.getMessage(), cause);
        this.code = code;
        this.variables = variables;
    }

    public JobWorkerException(String code, String cause, @Nullable Map<String, Object> variables) {
        super(cause);
        this.code = code;
        this.variables = variables;
    }

    public String getCode() {
        return code;
    }

    @Nullable
    public Map<String, Object> getVariables() {
        return variables;
    }

    @Override
    public String toString() {
        String s = getClass().getName();
        String message = getLocalizedMessage();
        return (message != null) ? (s + ": " + "[" + code + "]" + message) : "[" + code + "]" + s;
    }
}
