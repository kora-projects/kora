package io.koraframework.nats.common.producer.telemetry.impl;

import io.koraframework.logging.common.masking.MaskingStrategy;
import io.koraframework.nats.common.producer.telemetry.impl.DefaultNatsPublisherTelemetry.TelemetryContext;
import io.nats.client.Message;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultNatsPublisherLoggerFactory {
    public static final DefaultNatsPublisherLoggerFactory INSTANCE = new DefaultNatsPublisherLoggerFactory(value -> "***");
    protected final MaskingStrategy maskingStrategy;

    public DefaultNatsPublisherLoggerFactory(MaskingStrategy maskingStrategy) {
        this.maskingStrategy = maskingStrategy;
    }

    public DefaultNatsPublisherLogger create(TelemetryContext context) {
        var config = context.config();
        return new DefaultNatsPublisherLogger(context, maskingStrategy, config.logging().maskHeaders(), config.logging().logBody());
    }

    public static class DefaultNatsPublisherLogger {
        protected final TelemetryContext context;
        protected final Logger logger;
        protected final MaskingStrategy masking;
        protected final Set<String> maskedHeaders;
        protected final boolean logBody;

        public DefaultNatsPublisherLogger(TelemetryContext context, MaskingStrategy masking, Set<String> maskedHeaders, boolean logBody) {
            this.context = context;
            this.logger = LoggerFactory.getLogger(context.canonicalName());
            this.masking = masking;
            this.maskedHeaders = maskedHeaders.stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
            this.logBody = logBody;
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
