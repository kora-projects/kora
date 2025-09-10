package ru.tinkoff.kora.micrometer.module.soap.client;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;
import ru.tinkoff.kora.soap.client.common.SoapResult;
import ru.tinkoff.kora.soap.client.common.telemetry.SoapClientMetrics;
import ru.tinkoff.kora.soap.client.common.telemetry.SoapClientTelemetry;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.concurrent.TimeUnit;

public class OpentelemetrySoapClientMetrics implements SoapClientMetrics {
    private final Timer successDuration;
    private final Timer failureDuration;

    public OpentelemetrySoapClientMetrics(MeterRegistry meterRegistry, TelemetryConfig.MetricsConfig config, String service, String method, String host, int port) {
        this.successDuration = buildDuration(meterRegistry, config, service, method, host, port, "success");
        this.failureDuration = buildDuration(meterRegistry, config, service, method, host, port, "failure");
    }

    private static Timer buildDuration(MeterRegistry meterRegistry, TelemetryConfig.MetricsConfig config, String service, String method, String host, int port, String rpcResult) {
        var builder = Timer.builder("rpc.client.duration")
            .serviceLevelObjectives(config.slo())
            .tag(RpcIncubatingAttributes.RPC_SYSTEM.getKey(), "soap")
            .tag(RpcIncubatingAttributes.RPC_SERVICE.getKey(), service)
            .tag(RpcIncubatingAttributes.RPC_METHOD.getKey(), method)
            .tag("rpc.result", rpcResult)
            .tag(ServerAttributes.SERVER_ADDRESS.getKey(), host)
            .tag(ServerAttributes.SERVER_PORT.getKey(), Integer.toString(port));

        return builder.register(meterRegistry);
    }

    @Override
    public void recordSuccess(SoapResult.Success result, long processingTimeNanos) {
        this.successDuration.record(processingTimeNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void recordFailure(SoapClientTelemetry.SoapTelemetryContext.SoapClientFailure failure, long processingTimeNanos) {
        this.failureDuration.record(processingTimeNanos, TimeUnit.NANOSECONDS);
    }
}
