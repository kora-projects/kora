package ru.tinkoff.kora.micrometer.module.soap.client;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import ru.tinkoff.kora.micrometer.module.MetricsConfig.SoapClientMetricsConfig;
import ru.tinkoff.kora.soap.client.common.SoapResult;
import ru.tinkoff.kora.soap.client.common.telemetry.SoapClientMetrics;
import ru.tinkoff.kora.soap.client.common.telemetry.SoapClientTelemetry;

import javax.annotation.Nullable;

public class MicrometerSoapClientMetrics implements SoapClientMetrics {
    private final DistributionSummary successDuration;
    private final DistributionSummary failureDuration;

    public MicrometerSoapClientMetrics(MeterRegistry meterRegistry, @Nullable SoapClientMetricsConfig config, String service, String method, String host, int port) {
        this.successDuration = buildDuration(meterRegistry, config, service, method, host, port, "success");
        this.failureDuration = buildDuration(meterRegistry, config, service, method, host, port, "failure");
    }

    private static DistributionSummary buildDuration(MeterRegistry meterRegistry, @Nullable SoapClientMetricsConfig config, String service, String method, String host, int port, String rpcResult) {
        var builder = DistributionSummary.builder("rpc.client.duration");

        if (config != null && config.slo() != null) {
            builder.serviceLevelObjectives(config.slo().stream().mapToDouble(Double::doubleValue).toArray());
        } else {
            builder.serviceLevelObjectives(1, 10, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000, 30000, 60000, 90000);
        }

        return builder
            .baseUnit("milliseconds")
            .tag("rpc.system", "soap")
            .tag("rpc.service", service)
            .tag("rpc.method", method)
            .tag("rpc.result", rpcResult)
            .tag("net.peer.name", host)
            .tag("net.peer.port", Integer.toString(port))
            .register(meterRegistry);
    }

    @Override
    public void recordSuccess(SoapResult.Success result, long processingTime) {
        this.successDuration.record(((double) processingTime) / 1_000_000);
    }

    @Override
    public void recordFailure(SoapClientTelemetry.SoapTelemetryContext.SoapClientFailure failure, long processingTime) {
        this.failureDuration.record(((double) processingTime) / 1_000_000);
    }
}
