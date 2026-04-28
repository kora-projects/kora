package io.koraframework.redis.jedis.telemetry;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.semconv.DbAttributes;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;
import org.jspecify.annotations.Nullable;
import redis.clients.jedis.CommandObject;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class DefaultJedisMetricsFactory {

    public static final DefaultJedisMetricsFactory INSTANCE = new DefaultJedisMetricsFactory();

    public DefaultJedisMetrics create(DefaultJedisTelemetry.TelemetryContext context) {
        return new DefaultJedisMetrics(context);
    }

    public static class DefaultJedisMetrics {

        protected final ConcurrentHashMap<DurationKey, Timer> duration = new ConcurrentHashMap<>();

        protected final DefaultJedisTelemetry.TelemetryContext context;

        protected record DurationKey(String command, @Nullable Class<? extends Throwable> errorType) {}

        public DefaultJedisMetrics(DefaultJedisTelemetry.TelemetryContext context) {
            this.context = context;
        }

        public void recordFailure(CommandObject<?> command,
                                  String commandName,
                                  Throwable exception,
                                  long processingTimeNanos) {
            var errorType = exception.getClass();
            var key = new DurationKey(commandName, errorType);
            var metrics = this.duration.computeIfAbsent(key, _ -> {
                return createMetricCommandDuration(command, commandName, null, exception)
                    .register(context.meterRegistry());
            });

            metrics.record(processingTimeNanos, TimeUnit.NANOSECONDS);
        }

        public void recordSuccess(CommandObject<?> command,
                                  String commandName,
                                  Object result,
                                  long processingTimeNanos) {
            var key = new DurationKey(commandName, null);
            var metrics = this.duration.computeIfAbsent(key, _ -> {
                return createMetricCommandDuration(command, commandName, result, null)
                    .register(context.meterRegistry());
            });

            metrics.record(processingTimeNanos, TimeUnit.NANOSECONDS);
        }

        protected Timer.Builder createMetricCommandDuration(CommandObject<?> commandObject,
                                                            String commandName,
                                                            @Nullable Object result,
                                                            @Nullable Throwable error) {
            var builder = Timer.builder("db.client.operation.duration")
                .serviceLevelObjectives(this.context.config().metrics().slo());

            var tags = new ArrayList<Tag>(7 + this.context.config().metrics().tags().size());
            tags.add(Tag.of(DbAttributes.DB_SYSTEM_NAME.getKey(), "redis"));
            tags.add(Tag.of(RpcIncubatingAttributes.RPC_SERVICE.getKey(), "jedis"));
            tags.add(Tag.of(DbAttributes.DB_OPERATION_NAME.getKey(), commandName));
            if (error != null) {
                tags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), error.getClass().getCanonicalName()));
            } else {
                tags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), ""));
            }
            builder.tags(tags);

            for (var tag : this.context.config().metrics().tags().entrySet()) {
                builder.tag(tag.getKey(), tag.getValue());
            }

            return builder;
        }
    }
}
