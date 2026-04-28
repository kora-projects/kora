package io.koraframework.redis.jedis.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.ErrorAttributes;
import org.jspecify.annotations.Nullable;
import redis.clients.jedis.CommandObject;

public class DefaultJedisObservation implements JedisObservation {

    protected final DefaultJedisTelemetry.TelemetryContext context;
    protected final CommandObject<?> commandObject;
    protected final String commandName;
    protected final Span span;

    @Nullable
    protected Object result;
    @Nullable
    protected Throwable throwable;

    public DefaultJedisObservation(DefaultJedisTelemetry.TelemetryContext context,
                                   CommandObject<?> commandObject,
                                   String commandName,
                                   Span span) {
        this.context = context;
        this.commandObject = commandObject;
        this.commandName = commandName;
        this.span = span;
    }

    @Override
    public Span span() {
        return span;
    }

    @Override
    public void observeResult(Object result) {

    }

    @Override
    public void observeError(Throwable e) {
        if (span != null) {
            span.setStatus(StatusCode.ERROR);
            span.setAttribute(ErrorAttributes.ERROR_TYPE, e.getClass().getName());
            span.recordException(e);
        }
    }

    @Override
    public void end() {
        if (span != null) {
            span.end();
        }
    }
}
