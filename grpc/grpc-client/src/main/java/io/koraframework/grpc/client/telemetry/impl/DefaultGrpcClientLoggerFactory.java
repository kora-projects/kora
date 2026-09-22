package io.koraframework.grpc.client.telemetry.impl;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.koraframework.logging.common.arg.StructuredArgument;
import io.koraframework.logging.common.masking.MaskingStrategy;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultGrpcClientLoggerFactory {

    public static final DefaultGrpcClientLoggerFactory INSTANCE = new DefaultGrpcClientLoggerFactory(value -> "***");

    private final MaskingStrategy maskingStrategy;

    public DefaultGrpcClientLoggerFactory() {
        this(value -> "***");
    }

    public DefaultGrpcClientLoggerFactory(MaskingStrategy maskingStrategy) {
        this.maskingStrategy = maskingStrategy;
    }

    public DefaultGrpcClientLogger create(DefaultGrpcClientTelemetry.TelemetryContext context) {
        var requestLog = LoggerFactory.getLogger(context.service().getName() + ".request");
        var responseLog = LoggerFactory.getLogger(context.service().getName() + ".response");
        return new DefaultGrpcClientLogger(requestLog, responseLog, this.maskingStrategy, context);
    }

    public static class DefaultGrpcClientLogger {

        protected final Logger requestLog;
        protected final Logger responseLog;
        protected final DefaultGrpcClientTelemetry.TelemetryContext context;
        protected final MaskingStrategy maskingStrategy;
        protected final Set<String> maskedHeaders;

        public DefaultGrpcClientLogger(Logger requestLog,
                                       Logger responseLog,
                                       DefaultGrpcClientTelemetry.TelemetryContext context) {
            this(requestLog, responseLog, value -> "***", context);
        }

        public DefaultGrpcClientLogger(Logger requestLog,
                                       Logger responseLog,
                                       MaskingStrategy maskingStrategy,
                                       DefaultGrpcClientTelemetry.TelemetryContext context) {
            this.requestLog = requestLog;
            this.responseLog = responseLog;
            this.maskingStrategy = maskingStrategy;
            this.maskedHeaders = context.config().logging().maskHeaders().stream()
                .map(key -> key.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
            this.context = context;
        }

        public void logRequest(MethodDescriptor<?, ?> method, Metadata requestHeaders) {
            if (!this.requestLog.isInfoEnabled()) {
                return;
            }
            var headers = this.requestLog.isDebugEnabled() ? metadataToString(requestHeaders, this.maskedHeaders, this.maskingStrategy) : null;
            var service = Objects.requireNonNullElse(this.context.service().getName(), "GrpcService");
            var methodName = Objects.requireNonNullElse(method.getBareMethodName(), "");
            this.requestLog.atInfo()
                .addKeyValue("grpcRequest", StructuredArgument.value(gen -> {
                    gen.writeStartObject();
                    gen.writeStringProperty("serviceName", service);
                    gen.writeStringProperty("operation", service + "/" + methodName);
                    if (headers != null) {
                        gen.writeStringProperty("headers", headers);
                    }
                    gen.writeEndObject();
                }))
                .log("GrpcClient request started");
        }

        public void logResponse(MethodDescriptor<?, ?> method, @Nullable Status status, @Nullable Throwable error, long processingTimeNanos) {
            if (error == null && !this.responseLog.isInfoEnabled()) {
                return;
            }
            if (error != null && !this.responseLog.isWarnEnabled()) {
                return;
            }
            var service = Objects.requireNonNullElse(this.context.service().getName(), "GrpcService");
            var methodName = Objects.requireNonNullElse(method.getBareMethodName(), "");
            var statusCode = status == null ? Status.Code.UNKNOWN : status.getCode();
            var exceptionType = error == null ? null : error.getClass().getCanonicalName();
            var arg = StructuredArgument.value(gen -> {
                gen.writeStartObject();
                gen.writeStringProperty("serviceName", service);
                gen.writeStringProperty("operation", service + "/" + methodName);
                gen.writeNumberProperty("processingTime", processingTimeNanos / 1_000_000);
                gen.writeStringProperty("status", statusCode.name());
                if (exceptionType != null) {
                    gen.writeStringProperty("exceptionType", exceptionType);
                }
                gen.writeEndObject();
            });
            if (error == null) {
                this.responseLog.atInfo()
                    .addKeyValue("grpcResponse", arg)
                    .log("GrpcClient response received");
            } else {
                this.responseLog.atWarn()
                    .addKeyValue("grpcResponse", arg)
                    .setCause(error)
                    .log("GrpcClient received error");
            }
        }

        static String metadataToString(Metadata metadata, Set<String> maskedHeaders, MaskingStrategy maskingStrategy) {
            var result = new StringBuilder();
            for (var key : metadata.keys()) {
                if (key.endsWith(Metadata.BINARY_HEADER_SUFFIX)) {
                    var values = metadata.getAll(Metadata.Key.of(key, Metadata.BINARY_BYTE_MARSHALLER));
                    if (values != null) {
                        for (var value : values) {
                            appendMetadata(result, key,
                                maskedHeaders.contains(key) ? maskingStrategy.mask(value) : Base64.getEncoder().encodeToString(value));
                        }
                    }
                } else {
                    var values = metadata.getAll(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER));
                    if (values != null) {
                        for (var value : values) {
                            appendMetadata(result, key,
                                maskedHeaders.contains(key) ? maskingStrategy.mask(value) : value);
                        }
                    }
                }
            }
            return result.toString();
        }

        private static void appendMetadata(StringBuilder result, String key, String value) {
            if (!result.isEmpty()) {
                result.append('\n');
            }
            result.append(key).append(": ").append(value);
        }
    }
}
