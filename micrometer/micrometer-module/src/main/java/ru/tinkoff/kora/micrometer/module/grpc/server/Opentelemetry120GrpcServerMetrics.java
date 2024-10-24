package ru.tinkoff.kora.micrometer.module.grpc.server;

import io.grpc.Status;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.grpc.server.telemetry.GrpcServerMetrics;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class Opentelemetry120GrpcServerMetrics implements GrpcServerMetrics {
    private final Map<Integer, DistributionSummary> duration = new ConcurrentHashMap<>();
    private final Function<Integer, DistributionSummary> durationFactory;
    private final Counter requestsPerRpc;
    private final Counter responsesPerRpc;

    public Opentelemetry120GrpcServerMetrics(Function<Integer, DistributionSummary> durationFactory, Counter requestsPerRpc, Counter responsesPerRpc) {
        this.durationFactory = durationFactory;
        this.requestsPerRpc = requestsPerRpc;
        this.responsesPerRpc = responsesPerRpc;
    }

    @Override
    public void onClose(@Nullable Status status, @Nullable Throwable exception, long processingTimeNano) {
        var durationValue = ((double) processingTimeNano) / 1_000_000;
        var finalStatus = Objects.requireNonNullElse(status, Status.UNKNOWN);
        duration.computeIfAbsent(finalStatus.getCode().value(), durationFactory).record(durationValue);
    }

    @Override
    public void onSend(Object message) {
        this.responsesPerRpc.increment();
    }

    @Override
    public void onReceive(Object message) {
        this.requestsPerRpc.increment();
    }

}
