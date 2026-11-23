package io.koraframework.redis.lettuce.telemetry;

import io.lettuce.core.metrics.CommandLatencyRecorder;
import io.lettuce.core.protocol.ProtocolKeyword;
import io.lettuce.core.protocol.RedisCommand;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.semconv.DbAttributes;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLogger;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public class DefaultLettuceTelemetry implements CommandLatencyRecorder {

    protected record Key(String serverAddress, String serverPort, String command, @Nullable String error) {}

    protected record Metrics(Timer completion, Timer firstResponse) {}

    protected static final String METRIC_COMPLETION = "db.client.operation.duration";
    protected static final String METRIC_FIRST_RESPONSE = "lettuce.command.firstresponse.duration";

    protected static final String LABEL_TYPE = "lettuce.type";

    protected final ConcurrentMap<Key, Metrics> summary = new ConcurrentHashMap<>();

    protected final Logger logger;
    protected final String type;
    @Nullable
    protected final MeterRegistry registry;
    protected final LettuceTelemetryConfig config;

    public DefaultLettuceTelemetry(String type,
                                   @Nullable MeterRegistry registry,
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
        String serverAddress;
        String serverPort;
        if (remote instanceof InetSocketAddress inet) {
            serverAddress = inet.getHostString();
            serverPort = Integer.toString(inet.getPort());
        } else {
            serverAddress = remote.toString();
            serverPort = "";
        }
        String commandName = command.getType().toString();
        String error = null;
        if (command.getOutput().hasError()) {
            error = command.getOutput().getError();
        }

        if (this.registry != null) {
            var key = new Key(serverAddress, serverPort, commandName, errorType(error));
            var metrics = this.summary.computeIfAbsent(key, this::metrics);
            metrics.completion().record(completionLatencyInNanos, TimeUnit.NANOSECONDS);
            metrics.firstResponse().record(firstResponseLatencyInNanos, TimeUnit.NANOSECONDS);
        }

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

    // redis error prefix (ERR, MOVED, WRONGTYPE...) instead of full message to keep tag cardinality bounded
    @Nullable
    protected static String errorType(@Nullable String error) {
        if (error == null) {
            return null;
        }
        var space = error.indexOf(' ');
        var prefix = space == -1 ? error : error.substring(0, space);
        return prefix.isEmpty() ? "ERR" : prefix;
    }

    private Metrics metrics(Key key) {
        var registry = Objects.requireNonNull(this.registry);
        return new Metrics(
            this.createMetricCompleteDuration(key).register(registry),
            this.createMetricFirstResponseDuration(key).register(registry)
        );
    }

    protected Timer.Builder createMetricCompleteDuration(Key key) {
        var builder = Timer.builder(METRIC_COMPLETION)
            .description("Latency summary of Redis Lettuce commands completion")
            .serviceLevelObjectives(this.config.metrics().slo())
            .tag(DbAttributes.DB_SYSTEM_NAME.getKey(), "redis")
            .tag(DbAttributes.DB_OPERATION_NAME.getKey(), key.command())
            .tag(ServerAttributes.SERVER_ADDRESS.getKey(), key.serverAddress())
            .tag(ServerAttributes.SERVER_PORT.getKey(), key.serverPort())
            .tag(LABEL_TYPE, this.type);
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
            .tag(DbAttributes.DB_SYSTEM_NAME.getKey(), "redis")
            .tag(DbAttributes.DB_OPERATION_NAME.getKey(), key.command())
            .tag(ServerAttributes.SERVER_ADDRESS.getKey(), key.serverAddress())
            .tag(ServerAttributes.SERVER_PORT.getKey(), key.serverPort())
            .tag(LABEL_TYPE, this.type);
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
