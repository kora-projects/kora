package ru.tinkoff.kora.micrometer.module.soap.client;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.soap.client.common.telemetry.SoapClientMetrics;
import ru.tinkoff.kora.soap.client.common.telemetry.SoapClientMetricsFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.net.URI;
import java.util.Objects;

public class MicrometerSoapClientMetricsFactory implements SoapClientMetricsFactory {
    private final MeterRegistry meterRegistry;

    public MicrometerSoapClientMetricsFactory(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    @Nullable
    public SoapClientMetrics get(TelemetryConfig.MetricsConfig config, String serviceName, String soapMethod, String url) {
        if (Objects.requireNonNullElse(config.enabled(), true)) {
            var uri = URI.create(url);
            var host = uri.getHost();
            var scheme = uri.getScheme();
            var port = uri.getPort() != -1 ? uri.getPort() : switch (scheme) {
                case "http" -> 80;
                case "https" -> 443;
                default -> -1;
            };
            return new MicrometerSoapClientMetrics(this.meterRegistry, config, serviceName, soapMethod, host, port);
        } else {
            return null;
        }
    }
}
