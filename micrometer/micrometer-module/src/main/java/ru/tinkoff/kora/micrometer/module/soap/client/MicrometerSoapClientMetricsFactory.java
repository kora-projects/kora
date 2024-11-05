package ru.tinkoff.kora.micrometer.module.soap.client;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.micrometer.module.MetricsConfig;
import ru.tinkoff.kora.soap.client.common.telemetry.SoapClientMetrics;
import ru.tinkoff.kora.soap.client.common.telemetry.SoapClientMetricsFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.net.URI;
import java.util.Objects;

public class MicrometerSoapClientMetricsFactory implements SoapClientMetricsFactory {

    private final MeterRegistry meterRegistry;
    private final MetricsConfig metricsConfig;

    public MicrometerSoapClientMetricsFactory(MeterRegistry meterRegistry, MetricsConfig metricsConfig) {
        this.meterRegistry = meterRegistry;
        this.metricsConfig = metricsConfig;
    }

    @Nullable
    @Override
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

            int packageEnd = serviceName.lastIndexOf('.');
            String serviceSimpleName = (packageEnd == -1)
                ? serviceName
                : serviceName.substring(packageEnd);

            return switch (this.metricsConfig.opentelemetrySpec()) {
                case V120 -> new Opentelemetry120SoapClientMetrics(this.meterRegistry, config, serviceSimpleName, soapMethod, host, port);
                case V123 -> new Opentelemetry123SoapClientMetrics(this.meterRegistry, config, serviceSimpleName, soapMethod, host, port);
            };
        } else {
            return null;
        }
    }
}
