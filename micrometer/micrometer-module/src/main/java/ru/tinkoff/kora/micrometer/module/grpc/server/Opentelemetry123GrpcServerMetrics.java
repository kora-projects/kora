package ru.tinkoff.kora.micrometer.module.grpc.server;

import io.grpc.Status;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import ru.tinkoff.kora.grpc.server.telemetry.GrpcServerMetrics;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class Opentelemetry123GrpcServerMetrics implements GrpcServerMetrics {
    private final Map<Status, DistributionSummary> duration = new ConcurrentHashMap<>();
    private final Function<Status, DistributionSummary> durationFactory;
    private final Counter requestsPerRpc;
    private final Counter responsesPerRpc;

    public Opentelemetry123GrpcServerMetrics(Function<Status, DistributionSummary> durationFactory, Counter requestsPerRpc, Counter responsesPerRpc) {
        this.durationFactory = durationFactory;
        this.requestsPerRpc = requestsPerRpc;
        this.responsesPerRpc = responsesPerRpc;
    }

    @Override
    public void onClose(Status status, Throwable exception, long processingTimeNano) {
        var durationValue = ((double) processingTimeNano) / 1_000_000_000;
        var finalStatus = Objects.requireNonNullElse(status, Status.UNKNOWN);
        duration.computeIfAbsent(finalStatus, durationFactory).record(durationValue);
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
