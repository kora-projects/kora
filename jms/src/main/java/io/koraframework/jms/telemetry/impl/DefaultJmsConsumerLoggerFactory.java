package io.koraframework.jms.telemetry.impl;

import io.koraframework.jms.util.JmsUtils;
import io.koraframework.logging.common.arg.StructuredArgumentWriter;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

import javax.jms.JMSException;
import javax.jms.Message;

public class DefaultJmsConsumerLoggerFactory {

    public static final DefaultJmsConsumerLoggerFactory INSTANCE = new DefaultJmsConsumerLoggerFactory();

    public DefaultJmsConsumerLogger create(DefaultJmsConsumerTelemetry.TelemetryContext context) {
        var logger = LoggerFactory.getLogger("io.koraframework.jms.consumer." + context.queueName());
        return new DefaultJmsConsumerLogger(logger, context);
    }

    public static class DefaultJmsConsumerLogger {

        protected final Logger logger;
        protected final DefaultJmsConsumerTelemetry.TelemetryContext context;

        public DefaultJmsConsumerLogger(Logger logger, DefaultJmsConsumerTelemetry.TelemetryContext context) {
            this.logger = logger;
            this.context = context;
        }

        public void logStart(Message message, String destination) {
            if (!logger.isDebugEnabled()) {
                return;
            }
            var arg = (StructuredArgumentWriter) gen -> {
                gen.writeStartObject();
                gen.writeStringProperty("destination", destination);
                gen.writeStringProperty("queue", this.context.queueName());
                gen.writeEndObject();
            };
            logger.atDebug()
                .addKeyValue("jmsConsumer", arg)
                .log("JmsConsumer message received");
        }

        public void logProcess(Message message, String destination) throws JMSException {
            if (!logger.isTraceEnabled()) {
                return;
            }

            var headers = JmsUtils.dumpHeaders(message).toString();
            var body = JmsUtils.text(message);
            var arg = (StructuredArgumentWriter) gen -> {
                gen.writeStartObject();
                gen.writeStringProperty("destination", destination);
                gen.writeStringProperty("queue", this.context.queueName());
                gen.writeStringProperty("headers", headers);
                gen.writeStringProperty("body", body);
                gen.writeEndObject();
            };
            logger.atTrace()
                .addKeyValue("jmsConsumer", arg)
                .log("JmsConsumer message processing");
        }

        public void logEnd(Message message, String destination, long processingTimeNanos, @Nullable Throwable exception) {
            final LoggingEventBuilder event;
            if (exception != null) {
                if (!logger.isWarnEnabled()) {
                    return;
                }
                event = logger.atWarn();
            } else if (logger.isDebugEnabled()) {
                event = logger.atDebug();
            } else if (logger.isInfoEnabled()) {
                event = logger.atInfo();
            } else {
                return;
            }

            var arg = (StructuredArgumentWriter) gen -> {
                gen.writeStartObject();
                gen.writeStringProperty("destination", destination);
                gen.writeStringProperty("queue", this.context.queueName());
                gen.writeNumberProperty("processingTime", processingTimeNanos / 1_000_000);
                if (exception != null) {
                    var exceptionType = exception.getClass().getCanonicalName();
                    if (exceptionType != null) {
                        gen.writeStringProperty("exceptionType", exceptionType);
                    }
                    if (exception.getMessage() != null) {
                        gen.writeStringProperty("exceptionMessage", exception.getMessage());
                    }
                }
                gen.writeEndObject();
            };

            event.addKeyValue("jmsConsumer", arg);
            if (exception != null) {
                event.log("JmsConsumer message processed with error");
            } else {
                event.log("JmsConsumer message processed");
            }
        }
    }
}
