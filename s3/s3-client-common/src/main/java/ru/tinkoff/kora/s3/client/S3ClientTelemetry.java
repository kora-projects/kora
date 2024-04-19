package ru.tinkoff.kora.s3.client;

public interface S3ClientTelemetry {

    void beforeExecution();

    void afterExecution();

    void afterMarshalling();

    void onExecutionFailure();
}
