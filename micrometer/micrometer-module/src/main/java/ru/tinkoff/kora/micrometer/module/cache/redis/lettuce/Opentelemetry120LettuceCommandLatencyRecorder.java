package ru.tinkoff.kora.micrometer.module.cache.redis.lettuce;

import io.lettuce.core.metrics.CommandLatencyRecorder;
import io.lettuce.core.protocol.ProtocolKeyword;
import io.lettuce.core.protocol.RedisCommand;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.netty.channel.local.LocalAddress;
import io.opentelemetry.semconv.ErrorAttributes;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.net.SocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class Opentelemetry120LettuceCommandLatencyRecorder implements CommandLatencyRecorder {

    private static final String LABEL_TYPE = "type";
    private static final String LABEL_COMMAND = "command";
    private static final String LABEL_LOCAL = "local";
    private static final String LABEL_REMOTE = "remote";
    private static final String METRIC_COMPLETION = "lettuce.command.completion.duration";
    private static final String METRIC_FIRST_RESPONSE = "lettuce.command.firstresponse.duration";

    record Key(String local, String remote, String command, @Nullable String error) {}

    record Value(DistributionSummary completion, DistributionSummary firstResponse) {}

    private final ConcurrentMap<Key, Value> summary = new ConcurrentHashMap<>();

    private final String type;
    private final MeterRegistry registry;
    private final TelemetryConfig.MetricsConfig config;

    public Opentelemetry120LettuceCommandLatencyRecorder(String type,
                                                         MeterRegistry registry,
                                                         TelemetryConfig.MetricsConfig config) {
        this.type = type;
        this.registry = registry;
        this.config = config;
    }

    @Override
    public void recordCommandLatency(SocketAddress socketAddress, SocketAddress socketAddress1, ProtocolKeyword protocolKeyword, long l, long l1) {
        // ignore old impl
    }

    @Override
    public void recordCommandLatency(SocketAddress local, SocketAddress remote, RedisCommand<?, ?, ?> command, long firstResponseLatency, long completionLatency) {
        String remoteString = remote.toString();
        String commandName = command.getType().toString();
        String error = null;
        if (command.getOutput().hasError()) {
            error = command.getOutput().getError();
        }

        var key = new Key(LocalAddress.ANY.toString(), remoteString, commandName, error);
        var summary = this.summary.computeIfAbsent(key, this::metrics);
        summary.completion().record((double) completionLatency / 1_000_000);
        summary.firstResponse().record((double) firstResponseLatency / 1_000_000);
    }

    private Value metrics(Key key) {
        return new Value(this.metricsCompletion(key), this.metricFirstResponse(key));
    }

    private DistributionSummary metricsCompletion(Key key) {
        var builder = DistributionSummary.builder(METRIC_COMPLETION)
            .description("Latency summary of Redis Lettuce commands completion")
            .serviceLevelObjectives(this.config.slo(TelemetryConfig.MetricsConfig.OpentelemetrySpec.V120))
            .baseUnit("milliseconds")
            .tag(LABEL_TYPE, this.type)
            .tag(LABEL_REMOTE, key.remote())
            .tag(LABEL_LOCAL, key.local())
            .tag(LABEL_COMMAND, key.command());

        if (key.error != null) {
            builder.tag(ErrorAttributes.ERROR_TYPE.getKey(), key.error);
        } else {
            builder.tag(ErrorAttributes.ERROR_TYPE.getKey(), "");
        }

        return builder.register(this.registry);
    }

    private DistributionSummary metricFirstResponse(Key key) {
        var builder = DistributionSummary.builder(METRIC_FIRST_RESPONSE)
            .description("Latency summary of Redis Lettuce commands first response interaction")
            .serviceLevelObjectives(this.config.slo(TelemetryConfig.MetricsConfig.OpentelemetrySpec.V120))
            .baseUnit("milliseconds")
            .tag(LABEL_TYPE, this.type)
            .tag(LABEL_REMOTE, key.remote())
            .tag(LABEL_LOCAL, key.local())
            .tag(LABEL_COMMAND, key.command());

        if (key.error != null) {
            builder.tag(ErrorAttributes.ERROR_TYPE.getKey(), key.error);
        } else {
            builder.tag(ErrorAttributes.ERROR_TYPE.getKey(), "");
        }

        return builder.register(this.registry);
    }
}
