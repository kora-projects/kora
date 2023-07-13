package ru.tinkoff.kora.micrometer.module.soap.client;

import io.micrometer.core.instrument.MeterRegistry;
import ru.tinkoff.kora.micrometer.module.MetricsConfig;
import ru.tinkoff.kora.soap.client.common.telemetry.SoapClientMetrics;
import ru.tinkoff.kora.soap.client.common.telemetry.SoapClientMetricsFactory;

import java.net.URI;

public class MicrometerSoapClientMetricsFactory implements SoapClientMetricsFactory {
    private final MeterRegistry meterRegistry;
    private final MetricsConfig.SoapClientMetricsConfig config;

    public MicrometerSoapClientMetricsFactory(MeterRegistry meterRegistry, MetricsConfig.SoapClientMetricsConfig config) {
        this.meterRegistry = meterRegistry;
        this.config = config;
    }

    @Override
    public SoapClientMetrics get(String serviceName, String soapMethod, String url) {
        var uri = URI.create(url);
        var host = uri.getHost();
        var scheme = uri.getScheme();
        var port = uri.getPort() != -1 ? uri.getPort() : switch (scheme) {
            case "http" -> 80;
            case "https" -> 443;
            default -> -1;
        };
        return new MicrometerSoapClientMetrics(this.meterRegistry, this.config, serviceName, soapMethod, host, port);
    }
}
