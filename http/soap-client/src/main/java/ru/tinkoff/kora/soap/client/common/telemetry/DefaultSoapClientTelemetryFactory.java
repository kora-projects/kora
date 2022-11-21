package ru.tinkoff.kora.soap.client.common.telemetry;

import javax.annotation.Nullable;

public class DefaultSoapClientTelemetryFactory implements SoapClientTelemetryFactory {
    @Nullable
    private final SoapClientMetricsFactory metricsFactory;

    public DefaultSoapClientTelemetryFactory(@Nullable SoapClientMetricsFactory metricsFactory) {
        this.metricsFactory = metricsFactory;
    }

    @Override
    public SoapClientTelemetry get(String serviceName, String soapMethod, String url) {
        var metrics = this.metricsFactory == null ? null : this.metricsFactory.get(serviceName, soapMethod, url);
        return requestEnvelope -> new DefaultSoapTelemetryContext(metrics);
    }
}
