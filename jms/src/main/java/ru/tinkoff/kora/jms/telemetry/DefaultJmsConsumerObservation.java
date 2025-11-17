package ru.tinkoff.kora.jms.telemetry;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.ErrorAttributes;
import org.slf4j.Logger;
import ru.tinkoff.kora.jms.JmsUtils;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;

import javax.jms.JMSException;
import javax.jms.Message;
import java.util.concurrent.TimeUnit;

public class DefaultJmsConsumerObservation implements JmsConsumerObservation {
    protected final long start = System.nanoTime();
    protected final Message message;
    protected final Span span;
    protected final Meter.MeterProvider<Timer> duration;
    protected final Logger logger;
    protected Throwable error;

    public DefaultJmsConsumerObservation(Message message, Span span, Meter.MeterProvider<Timer> duration, Logger logger) {
        this.message = message;
        this.span = span;
        this.duration = duration;
        this.logger = logger;
    }

    @Override
    public void observeProcess() throws JMSException {
        if (logger.isDebugEnabled()) {
            var body = JmsUtils.text(message);
            var headers = JmsUtils.dumpHeaders(message).toString();
            logger
                .atDebug()
                .addKeyValue("jmsInputMessage", StructuredArgument.value((gen) -> {
                    gen.writeStartObject();
                    gen.writeStringField("headers", headers);
                    gen.writeStringField("body", body);
                    gen.writeEndObject();
                }))
                .log("JmsListener.message");
        }
    }

    @Override
    public Span span() {
        return this.span;
    }

    @Override
    public void end() {
        if (this.error == null) {
            this.span.setStatus(StatusCode.ERROR);
        }
        var took = System.nanoTime() - this.start;
        this.duration.withTag(
            ErrorAttributes.ERROR_TYPE.getKey(), this.error == null ? "" : this.error.getClass().getCanonicalName()
        ).record(took, TimeUnit.NANOSECONDS);
    }

    @Override
    public void observeError(Throwable e) {
        this.error = e;
        this.span.setStatus(StatusCode.ERROR);
        this.span.recordException(e);
    }
}
