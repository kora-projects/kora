package io.koraframework.nats.common.consumer.telemetry.impl;

import io.koraframework.logging.common.masking.MaskingStrategy;
import io.koraframework.nats.common.consumer.deserializer.NatsDeserializer;
import io.koraframework.nats.common.consumer.telemetry.NatsConsumerTelemetryConfig;
import io.koraframework.nats.common.consumer.telemetry.impl.DefaultNatsConsumerTelemetry.TelemetryContext;
import io.nats.client.Message;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultNatsConsumerLoggerFactory {
    public static final DefaultNatsConsumerLoggerFactory INSTANCE = new DefaultNatsConsumerLoggerFactory(value -> "***");
    protected final MaskingStrategy maskingStrategy;
    protected final DefaultNatsConsumerBodyConverter bodyConverter;

    public DefaultNatsConsumerLoggerFactory(MaskingStrategy maskingStrategy) {
        this(maskingStrategy, new DefaultNatsConsumerBodyConverter());
    }

    public DefaultNatsConsumerLoggerFactory(MaskingStrategy maskingStrategy, DefaultNatsConsumerBodyConverter bodyConverter) {
        this.maskingStrategy = maskingStrategy;
        this.bodyConverter = bodyConverter;
    }

    public DefaultNatsConsumerLogger create(TelemetryContext context) {
        var config = context.config();
        return new DefaultNatsConsumerLogger(context, maskingStrategy, bodyConverter, config);
    }

    public static class DefaultNatsConsumerLogger {
        protected final TelemetryContext context;
        protected final Logger logger;
        protected final MaskingStrategy masking;
        protected final Set<String> maskedHeaders;
        protected final boolean logBody;
        private final DefaultNatsConsumerBodyConverter bodyConverter;

        public DefaultNatsConsumerLogger(TelemetryContext context, MaskingStrategy masking,
                                         DefaultNatsConsumerBodyConverter bodyConverter, NatsConsumerTelemetryConfig config) {
            this.context = context;
            this.logger = LoggerFactory.getLogger(context.canonicalName());
            this.masking = masking;
            this.maskedHeaders = config.logging().maskHeaders().stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
            this.logBody = config.logging().logBody();
            this.bodyConverter = bodyConverter;
        }

        public void start(String operation, String subject) {
            if (context.config().logging().enabled()) {
                logger.atTrace().addKeyValue("config", context.configPath())
                    .addKeyValue("subject", subject).addKeyValue("operation", operation).log("NATS operation started");
            }
        }

        public void record(Message message, @Nullable Object value) {
            if (!context.config().logging().enabled() || !logger.isTraceEnabled()) {
                return;
            }
            recordBody(message, value == null ? null : masking.mask(value));
        }

        public void record(Message message, NatsDeserializer<?> deserializer) {
            if (!context.config().logging().enabled() || !logger.isTraceEnabled()) {
                return;
            }
            recordBody(message, logBody ? bodyConverter.convert(message, deserializer) : null);
        }

        protected void recordBody(Message message, @Nullable Object body) {
            if (!context.config().logging().enabled() || !logger.isTraceEnabled()) {
                return;
            }
            var headers = new LinkedHashMap<String, Object>();
            if (message.hasHeaders()) {
                message.getHeaders().forEach((key, values) ->
                    headers.put(key, maskedHeaders.contains(key.toLowerCase(Locale.ROOT)) ? "***" : values));
            }
            var builder = logger.atTrace().addKeyValue("config", context.configPath()).addKeyValue("subject", message.getSubject())
                .addKeyValue("headers", headers).addKeyValue("bytes", message.getData() == null ? 0 : message.getData().length);
            if (logBody && body != null) {
                builder.addKeyValue("body", body);
            }
            builder.log("NATS record");
        }

        public void end(String operation, String subject, @Nullable Throwable error) {
            if (!context.config().logging().enabled()) {
                return;
            }
            var builder = error == null ? logger.atDebug() : logger.atWarn().setCause(error);
            builder.addKeyValue("config", context.configPath()).addKeyValue("subject", subject)
                .addKeyValue("operation", operation).log(error == null ? "NATS operation completed" : "NATS operation failed");
        }
    }
}
