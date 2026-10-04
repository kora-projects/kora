package io.koraframework.jms.telemetry.impl;

import io.koraframework.jms.util.JmsUtils;
import io.koraframework.logging.common.arg.StructuredArgumentWriter;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.TextMessage;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultJmsConsumerLoggerFactory {

    public static final DefaultJmsConsumerLoggerFactory INSTANCE = new DefaultJmsConsumerLoggerFactory();

    public DefaultJmsConsumerLogger create(DefaultJmsConsumerTelemetry.TelemetryContext context) {
        var logger = LoggerFactory.getLogger("io.koraframework.jms.consumer." + context.queueName());
        return new DefaultJmsConsumerLogger(logger, context);
    }

    public static class DefaultJmsConsumerLogger {

        protected final Logger logger;
        protected final DefaultJmsConsumerTelemetry.TelemetryContext context;
        private final Set<String> maskedHeaders;

        public DefaultJmsConsumerLogger(Logger logger, DefaultJmsConsumerTelemetry.TelemetryContext context) {
            this.logger = logger;
            this.context = context;
            var config = context.config().logging();
            if (config.maxBodyLogSize().toBytes() < 0 || config.maxBodyLogSize().toBytes() > Integer.MAX_VALUE
                || config.maxLoggedProperties() < 0 || config.maxPropertyValueLength() < 0) {
                throw new IllegalArgumentException("JMS logging limits must be nonnegative and body limit must fit an int");
            }
            this.maskedHeaders =
                config.maskHeaders().stream().map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
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
            logger.atDebug().addKeyValue("jmsConsumer", arg).log("JmsConsumer message received");
        }

        public void logProcess(Message message, String destination) throws JMSException {
            if (!logger.isTraceEnabled()) {
                return;
            }

            var headers = readHeaders(message).toString();
            var body = readBody(message);
            var arg = (StructuredArgumentWriter) gen -> {
                gen.writeStartObject();
                gen.writeStringProperty("destination", destination);
                gen.writeStringProperty("queue", this.context.queueName());
                gen.writeStringProperty("headers", headers);
                if (body != null) {
                    gen.writeStringProperty("body", body);
                }
                gen.writeEndObject();
            };
            logger.atTrace().addKeyValue("jmsConsumer", arg).log("JmsConsumer message processing");
        }

        /**
         * Bounded, case-insensitive property masking, following HTTP server header logging.
         */
        protected Map<String, String> readHeaders(Message message) {
            var headers = new HashMap<String, String>();
            try {
                var names = message.getPropertyNames();
                int count = 0;
                var config = context.config().logging();
                while (count < config.maxLoggedProperties() && names.hasMoreElements()) {
                    var name = (String) names.nextElement();
                    String value =
                        maskedHeaders.contains(name.toLowerCase(Locale.ROOT)) ? "***" : String.valueOf(message.getObjectProperty(name));
                    headers.put(limit(name, config.maxPropertyValueLength()), limit(value, config.maxPropertyValueLength()));
                    count++;
                }
            } catch (Exception e) {
                logger.warn("Failed to read JMS properties for '{}'", context.queueName(), e);
            }
            return headers;
        }

        /**
         * Unsupported or oversized bodies are omitted; logging must not decide delivery.
         */
        @Nullable
        protected String readBody(Message message) {
            var config = context.config().logging();
            if (!config.logBody() || config.maxBodyLogSize().toBytes() == 0
                || !(message instanceof TextMessage || message instanceof BytesMessage)) {
                return null;
            }
            try {
                return JmsUtils.text(message, Math.toIntExact(config.maxBodyLogSize().toBytes()));
            } catch (Exception e) {
                logger.debug("JMS body omitted from log for '{}': {}", context.queueName(), e.toString());
                return null;
            }
        }

        private static String limit(String value, int length) {
            return value.length() <= length ? value : value.substring(0, length);
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
                if (context.config().logging().stacktrace()) {
                    event.setCause(exception);
                }
                event.log("JmsConsumer message processed with error");
            } else {
                event.log("JmsConsumer message processed");
            }
        }
    }
}
