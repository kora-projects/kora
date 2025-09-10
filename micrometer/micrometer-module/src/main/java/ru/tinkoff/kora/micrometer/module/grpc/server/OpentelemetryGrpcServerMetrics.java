package ru.tinkoff.kora.micrometer.module.grpc.server;

import io.grpc.Status;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import ru.tinkoff.kora.grpc.server.telemetry.GrpcServerMetrics;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public final class OpentelemetryGrpcServerMetrics implements GrpcServerMetrics {
    private final Map<Integer, Timer> duration = new ConcurrentHashMap<>();
    private final Function<Integer, Timer> durationFactory;
    private final Counter requestsPerRpc;
    private final Counter responsesPerRpc;

    public OpentelemetryGrpcServerMetrics(Function<Integer, Timer> durationFactory, Counter requestsPerRpc, Counter responsesPerRpc) {
        this.durationFactory = durationFactory;
        this.requestsPerRpc = requestsPerRpc;
        this.responsesPerRpc = responsesPerRpc;
    }

    @Override
    public void onClose(Status status, Throwable exception, long processingTimeNano) {
        var finalStatus = Objects.requireNonNullElse(status, Status.UNKNOWN);
        duration.computeIfAbsent(finalStatus.getCode().value(), durationFactory).record(processingTimeNano, TimeUnit.NANOSECONDS);
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
