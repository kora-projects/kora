package ru.tinkoff.kora.soap.client.common.telemetry;

import ru.tinkoff.kora.soap.client.common.SoapResult;

import javax.annotation.Nullable;

public class DefaultSoapTelemetryContext implements SoapClientTelemetry.SoapTelemetryContext {
    @Nullable
    private final SoapClientMetrics metrics;
    private final long start;

    public DefaultSoapTelemetryContext(@Nullable SoapClientMetrics metrics) {
        this.start = System.nanoTime();
        this.metrics = metrics;
    }

    @Override
    public void success(SoapResult.Success result) {
        var processingTime = System.nanoTime() - this.start;
        if (this.metrics != null) {
            this.metrics.recordSuccess(result, processingTime);
        }
    }

    @Override
    public void failure(SoapClientFailure failure) {
        var processingTime = System.nanoTime() - this.start;
        if (this.metrics != null) {
            this.metrics.recordFailure(failure, processingTime);
        }
    }
}
