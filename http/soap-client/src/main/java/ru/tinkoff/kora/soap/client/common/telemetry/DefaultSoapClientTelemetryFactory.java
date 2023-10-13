package ru.tinkoff.kora.soap.client.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.soap.client.common.SoapResult;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public class DefaultSoapClientTelemetryFactory implements SoapClientTelemetryFactory {
    private static final SoapClientTelemetry.SoapTelemetryContext NOOP_CTX = new SoapClientTelemetry.SoapTelemetryContext() {
        @Override
        public void success(SoapResult.Success result) {

        }

        @Override
        public void failure(SoapClientFailure failure) {

        }
    };

    @Nullable
    private final SoapClientMetricsFactory metricsFactory;

    public DefaultSoapClientTelemetryFactory(@Nullable SoapClientMetricsFactory metricsFactory) {
        this.metricsFactory = metricsFactory;
    }

    @Override
    public SoapClientTelemetry get(TelemetryConfig config, String serviceName, String soapMethod, String url) {
        if (this.metricsFactory == null) {
            return envelope -> NOOP_CTX;
        }
        var metrics = this.metricsFactory.get(config.metrics(), serviceName, soapMethod, url);
        return requestEnvelope -> new DefaultSoapTelemetryContext(metrics);
    }
}
