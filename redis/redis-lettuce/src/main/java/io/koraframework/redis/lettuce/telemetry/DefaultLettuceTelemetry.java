package io.koraframework.redis.lettuce.telemetry;

import io.lettuce.core.metrics.CommandLatencyRecorder;
import io.lettuce.core.protocol.ProtocolKeyword;
import io.lettuce.core.protocol.RedisCommand;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.semconv.ErrorAttributes;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLogger;

import java.net.SocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public class DefaultLettuceTelemetry implements CommandLatencyRecorder {

    protected record Key(String addressRemote, String command, @Nullable String error) {}

    protected record Metrics(Timer completion, Timer firstResponse) {}

    protected static final String METRIC_COMPLETION = "lettuce.command.completion.duration";
    protected static final String METRIC_FIRST_RESPONSE = "lettuce.command.firstresponse.duration";

    protected static final String LABEL_TYPE = "type";
    protected static final String LABEL_COMMAND = "command";
    protected static final String LABEL_REMOTE = "remote";

    protected final ConcurrentMap<Key, Metrics> summary = new ConcurrentHashMap<>();

    protected final Logger logger;
    protected final String type;
    protected final MeterRegistry registry;
    protected final LettuceTelemetryConfig config;

    public DefaultLettuceTelemetry(String type,
                                   MeterRegistry registry,
                                   LettuceTelemetryConfig config) {
        this.logger = (config.logging().enabled())
            ? LoggerFactory.getLogger(DefaultLettuceTelemetry.class)
            : NOPLogger.NOP_LOGGER;
        this.type = type;
        this.registry = registry;
        this.config = config;
    }

    @Override
    public void recordCommandLatency(SocketAddress socketAddress, SocketAddress socketAddress1, ProtocolKeyword protocolKeyword, long l, long l1) {
        // ignore old impl
    }

    @Override
    public void recordCommandLatency(SocketAddress local, SocketAddress remote, RedisCommand<?, ?, ?> command, long firstResponseLatencyInNanos, long completionLatencyInNanos) {
        String remoteString = remote.toString();
        String commandName = command.getType().toString();
        String error = null;
        if (command.getOutput().hasError()) {
            error = command.getOutput().getError();
        }

        var key = new Key(remoteString, commandName, error);
        var metrics = this.summary.computeIfAbsent(key, this::metrics);
        metrics.completion().record(completionLatencyInNanos, TimeUnit.NANOSECONDS);
        metrics.firstResponse().record(firstResponseLatencyInNanos, TimeUnit.NANOSECONDS);

        if (error != null) {
            logger.atWarn()
                .addKeyValue("type", type)
                .addKeyValue("command", commandName)
                .log("Command execution failed due to: {}", error);
        } else {
            logger.atTrace()
                .addKeyValue("type", type)
                .addKeyValue("command", commandName)
                .log("Command execution success");
        }
    }

    private Metrics metrics(Key key) {
        return new Metrics(
            this.createMetricCompleteDuration(key).register(this.registry),
            this.createMetricFirstResponseDuration(key).register(this.registry)
        );
    }

    protected Timer.Builder createMetricCompleteDuration(Key key) {
        var builder = Timer.builder(METRIC_COMPLETION)
            .description("Latency summary of Redis Lettuce commands completion")
            .serviceLevelObjectives(this.config.metrics().slo())
            .tag(LABEL_TYPE, this.type)
            .tag(LABEL_REMOTE, key.addressRemote())
            .tag(LABEL_COMMAND, key.command());
        for (var e : this.config.metrics().tags().entrySet()) {
            builder.tag(e.getKey(), e.getValue());
        }

        if (key.error != null) {
            builder.tag(ErrorAttributes.ERROR_TYPE.getKey(), key.error);
        } else {
            builder.tag(ErrorAttributes.ERROR_TYPE.getKey(), "");
        }

        return builder;
    }

    protected Timer.Builder createMetricFirstResponseDuration(Key key) {
        var builder = Timer.builder(METRIC_FIRST_RESPONSE)
            .description("Latency summary of Redis Lettuce commands first response interaction")
            .serviceLevelObjectives(this.config.metrics().slo())
            .tag(LABEL_TYPE, this.type)
            .tag(LABEL_REMOTE, key.addressRemote())
            .tag(LABEL_COMMAND, key.command());
        for (var e : this.config.metrics().tags().entrySet()) {
            builder.tag(e.getKey(), e.getValue());
        }

        if (key.error != null) {
            builder.tag(ErrorAttributes.ERROR_TYPE.getKey(), key.error);
        } else {
            builder.tag(ErrorAttributes.ERROR_TYPE.getKey(), "");
        }

        return builder;
    }
}
