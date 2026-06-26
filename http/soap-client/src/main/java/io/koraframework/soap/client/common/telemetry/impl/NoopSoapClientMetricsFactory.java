package io.koraframework.soap.client.common.telemetry.impl;

import io.koraframework.http.client.common.response.HttpClientResponse;
import io.koraframework.soap.client.common.SoapResult;
import io.koraframework.soap.client.common.envelope.SoapEnvelope;
import org.jspecify.annotations.Nullable;

public final class NoopSoapClientMetricsFactory extends DefaultSoapClientMetricsFactory {

    public static final NoopSoapClientMetricsFactory INSTANCE = new NoopSoapClientMetricsFactory();

    private NoopSoapClientMetricsFactory() {}

    @Override
    public DefaultSoapClientMetrics create(DefaultSoapClientTelemetry.TelemetryContext context) {
        return NoopSoapClientMetrics.INSTANCE;
    }

    public static final class NoopSoapClientMetrics extends DefaultSoapClientMetrics {

        public static final NoopSoapClientMetrics INSTANCE = new NoopSoapClientMetrics();

        private NoopSoapClientMetrics() {
            super(DefaultSoapClientTelemetry.TelemetryContext.EMPTY);
        }

        @Override
        public void record(SoapEnvelope requestEnvelope,
                           @Nullable HttpClientResponse httpResponse,
                           SoapResult.@Nullable Failure failure,
                           @Nullable Throwable exception,
                           long processingTimeNanos) {

        }
    }
}
