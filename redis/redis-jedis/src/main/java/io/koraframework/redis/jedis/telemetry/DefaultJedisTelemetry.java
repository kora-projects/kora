package io.koraframework.redis.jedis.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.DbAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLogger;
import redis.clients.jedis.CommandObject;

public class DefaultJedisTelemetry implements JedisTelemetry {

    public record TelemetryContext(JedisTelemetryConfig config,
                                   boolean isTraceEnabled,
                                   boolean isMetricsEnabled,
                                   MeterRegistry meterRegistry,
                                   Tracer tracer,
                                   Logger logger) {}

    protected final TelemetryContext context;
    protected final DefaultJedisMetricsFactory.DefaultJedisMetrics metrics;

    public DefaultJedisTelemetry(JedisTelemetryConfig config,
                                 Tracer tracer,
                                 MeterRegistry meterRegistry,
                                 DefaultJedisMetricsFactory metricsFactory) {
        var isTraceEnabled = config.tracing().enabled() && tracer != DefaultJedisTelemetryFactory.NOOP_TRACER;
        var isMetricsEnabled = config.metrics().enabled() && meterRegistry != DefaultJedisTelemetryFactory.NOOP_METER_REGISTRY;

        var logger = config.logging().enabled()
            ? LoggerFactory.getLogger(JedisTelemetry.class)
            : NOPLogger.NOP_LOGGER;

        this.context = new TelemetryContext(
            config,
            isTraceEnabled,
            isMetricsEnabled,
            meterRegistry,
            tracer,
            logger
        );

        this.metrics = metricsFactory.create(context);
    }

    @Override
    public <T> JedisObservation observe(CommandObject<T> commandObject) {
        var commandName = getCommandName(commandObject);
        var span = context.config.tracing().enabled()
            ? createSpan(commandObject, commandName).startSpan()
            : Span.getInvalid();

        return new DefaultJedisObservation(context, commandObject, commandName, span);
    }

    protected SpanBuilder createSpan(CommandObject<?> commandObject, String commandName) {
        var span = this.context.tracer.spanBuilder("jedis.command")
            .setAttribute(DbAttributes.DB_SYSTEM_NAME.getKey(), "redis")
            .setAttribute(RpcIncubatingAttributes.RPC_SERVICE.getKey(), "jedis")
            .setAttribute(DbAttributes.DB_OPERATION_NAME.getKey(), commandName)
            .setSpanKind(SpanKind.CLIENT);

        for (var entry : this.context.config.tracing().attributes().entrySet()) {
            span.setAttribute(entry.getKey(), entry.getValue());
        }

        return span;
    }

    protected String getCommandName(CommandObject<?> commandObject) {
        return commandObject.getClass().getName();
    }
}
