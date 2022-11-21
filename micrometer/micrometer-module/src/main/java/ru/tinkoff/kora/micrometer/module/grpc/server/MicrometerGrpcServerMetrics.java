package ru.tinkoff.kora.micrometer.module.grpc.server;

import io.grpc.Status;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import ru.tinkoff.kora.grpc.telemetry.GrpcServerMetrics;

public final class MicrometerGrpcServerMetrics implements GrpcServerMetrics {
    private final DistributionSummary duration;
    private final Counter requestsPerRpc;
    private final Counter responsesPerRpc;

    public MicrometerGrpcServerMetrics(DistributionSummary duration, Counter requestsPerRpc, Counter responsesPerRpc) {
        this.duration = duration;
        this.requestsPerRpc = requestsPerRpc;
        this.responsesPerRpc = responsesPerRpc;
    }

    @Override
    public void onClose(Status status, Throwable exception, long processingTime) {
        double duration = ((double) processingTime) / 1_000_000;
        this.duration.record(duration);
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
