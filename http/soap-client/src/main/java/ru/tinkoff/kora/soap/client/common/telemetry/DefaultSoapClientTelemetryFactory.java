package ru.tinkoff.kora.soap.client.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.soap.client.common.SoapResult;
import ru.tinkoff.kora.soap.client.common.envelope.SoapEnvelope;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public class DefaultSoapClientTelemetryFactory implements SoapClientTelemetryFactory {

    private static final SoapClientTelemetry.SoapTelemetryContext NOOP_CTX = new SoapClientTelemetry.SoapTelemetryContext() {

        @Override
        public boolean prepareResponseBody() {
            return false;
        }

        @Override
        public void prepared(SoapEnvelope requestEnvelope, byte[] requestAsBytes) {}

        @Override
        public void success(SoapResult.Success success, @Nullable byte[] responseAsBytes) {}

        @Override
        public void failure(SoapClientFailure failure, @Nullable byte[] responseAsBytes) {}
    };

    @Nullable
    private final SoapClientLoggerFactory loggerFactory;
    @Nullable
    private final SoapClientMetricsFactory metricsFactory;
    @Nullable
    private final SoapClientTracerFactory tracingFactory;

    public DefaultSoapClientTelemetryFactory(@Nullable SoapClientLoggerFactory loggerFactory,
                                             @Nullable SoapClientMetricsFactory metricsFactory,
                                             @Nullable SoapClientTracerFactory tracingFactory) {
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
        this.tracingFactory = tracingFactory;
    }

    @Override
    public SoapClientTelemetry get(TelemetryConfig config, String serviceClass, String serviceName, String soapMethod, String url) {
        var tracing = this.tracingFactory == null ? null : this.tracingFactory.get(config.tracing(), serviceClass, serviceName, soapMethod, url);
        var metrics = this.metricsFactory == null ? null : this.metricsFactory.get(config.metrics(), serviceClass, serviceName, soapMethod, url);
        var logger = this.loggerFactory == null ? null : this.loggerFactory.get(config.logging(), serviceClass, serviceName, soapMethod, url);
        if (tracing == null && metrics == null && logger == null) {
            return envelope -> NOOP_CTX;
        }

        return requestEnvelope -> {
            var start = System.nanoTime();

            var span = (tracing == null)
                ? null
                : tracing.createSpan(Context.current(), requestEnvelope);

            return new SoapClientTelemetry.SoapTelemetryContext() {

                @Override
                public boolean prepareResponseBody() {
                    return logger != null && logger.logResponseBody();
                }

                @Override
                public void prepared(SoapEnvelope requestEnvelope, byte[] requestAsBytes) {
                    if (logger != null) {
                        logger.logRequest(requestEnvelope, requestAsBytes);
                    }
                }

                @Override
                public void success(SoapResult.Success success, @Nullable byte[] responseAsBytes) {
                    var processingTime = System.nanoTime() - start;
                    if (metrics != null) {
                        metrics.recordSuccess(success, processingTime);
                    }
                    if (span != null) {
                        span.success(success);
                    }
                    if (logger != null) {
                        logger.logSuccess(success, responseAsBytes);
                    }
                }

                @Override
                public void failure(SoapClientFailure failure, @Nullable byte[] responseAsBytes) {
                    var processingTime = System.nanoTime() - start;
                    if (metrics != null) {
                        metrics.recordFailure(failure, processingTime);
                    }
                    if (span != null) {
                        span.failure(failure);
                    }
                    if (logger != null) {
                        logger.logFailure(failure, responseAsBytes);
                    }
                }
            };
        };
    }
}
