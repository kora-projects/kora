package io.koraframework.grpc.server.telemetry.impl;

import io.grpc.Metadata;
import io.grpc.Status;
import io.koraframework.grpc.server.GrpcServer;
import io.koraframework.grpc.server.telemetry.GrpcServerArgMaskingStrategy;
import io.koraframework.logging.common.arg.StructuredArgument;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultGrpcServerLoggerFactory {

    public static final DefaultGrpcServerLoggerFactory INSTANCE = new DefaultGrpcServerLoggerFactory((key, value) -> "***");

    private final GrpcServerArgMaskingStrategy maskingStrategy;

    public DefaultGrpcServerLoggerFactory() {
        this((key, value) -> "***");
    }

    public DefaultGrpcServerLoggerFactory(GrpcServerArgMaskingStrategy maskingStrategy) {
        this.maskingStrategy = maskingStrategy;
    }

    public DefaultGrpcServerLogger create(DefaultGrpcServerTelemetry.TelemetryContext context) {
        var requestLog = LoggerFactory.getLogger(GrpcServer.class.getCanonicalName() + ".request");
        var responseLog = LoggerFactory.getLogger(GrpcServer.class.getCanonicalName() + ".response");
        return new DefaultGrpcServerLogger(context, requestLog, responseLog, this.maskingStrategy);
    }

    public static class DefaultGrpcServerLogger {

        protected final DefaultGrpcServerTelemetry.TelemetryContext context;
        protected final Logger requestLog;
        protected final Logger responseLog;
        protected final GrpcServerArgMaskingStrategy maskingStrategy;
        protected final Set<String> maskedHeaders;

        public DefaultGrpcServerLogger(DefaultGrpcServerTelemetry.TelemetryContext context, Logger requestLog, Logger responseLog) {
            this(context, requestLog, responseLog, (key, value) -> "***");
        }

        public DefaultGrpcServerLogger(DefaultGrpcServerTelemetry.TelemetryContext context,
                                       Logger requestLog,
                                       Logger responseLog,
                                       GrpcServerArgMaskingStrategy maskingStrategy) {
            this.context = context;
            this.requestLog = requestLog;
            this.responseLog = responseLog;
            this.maskingStrategy = maskingStrategy;
            this.maskedHeaders = context.config().logging().maskHeaders().stream()
                .map(key -> key.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        }

        public boolean logRequestBody() {
            return this.requestLog.isTraceEnabled();
        }

        public boolean logResponseBody() {
            return this.responseLog.isTraceEnabled();
        }

        public void logRequest(String service, String method, Metadata requestHeaders, @Nullable Object requestMessage) {
            if (!this.requestLog.isInfoEnabled()) {
                return;
            }
            var headers = this.requestLog.isDebugEnabled() ? metadataToString(requestHeaders, this.maskedHeaders, this.maskingStrategy) : null;
            var body = this.requestLog.isTraceEnabled()
                ? this.context.bodyConverter().convertRequestMessage(service, method, requestHeaders, requestMessage)
                : null;
            this.requestLog.atInfo()
                .addKeyValue("grpcRequest", StructuredArgument.value(gen -> {
                    gen.writeStartObject();
                    gen.writeStringProperty("serverName", this.context.name());
                    gen.writeNumberProperty("serverPort", this.context.port());
                    gen.writeStringProperty("serviceName", service);
                    gen.writeStringProperty("operation", service + "/" + method);
                    if (headers != null) {
                        gen.writeStringProperty("headers", headers);
                    }
                    if (body != null) {
                        gen.writeStringProperty("body", body);
                    }
                    gen.writeEndObject();
                }))
                .log("GrpcCall received");
        }

        public void logResponse(String service,
                                String method,
                                @Nullable Status status,
                                @Nullable Throwable error,
                                @Nullable Object responseMessage,
                                long processingTimeNanos) {
            if (error == null && !this.responseLog.isInfoEnabled()) {
                return;
            }
            if (error != null && !this.responseLog.isWarnEnabled()) {
                return;
            }
            var statusCode = status == null ? Status.Code.UNKNOWN : status.getCode();
            var exceptionType = error == null ? null : error.getClass().getCanonicalName();
            var body = this.responseLog.isTraceEnabled() && responseMessage != null
                ? this.context.bodyConverter().convertResponseMessage(responseMessage)
                : null;
            var arg = StructuredArgument.value(gen -> {
                gen.writeStartObject();
                gen.writeStringProperty("serverName", this.context.name());
                gen.writeNumberProperty("serverPort", this.context.port());
                gen.writeStringProperty("serviceName", service);
                gen.writeStringProperty("operation", service + "/" + method);
                gen.writeNumberProperty("processingTime", processingTimeNanos / 1_000_000);
                gen.writeStringProperty("status", statusCode.name());
                if (exceptionType != null) {
                    gen.writeStringProperty("exceptionType", exceptionType);
                }
                if (body != null) {
                    gen.writeStringProperty("body", body);
                }
                gen.writeEndObject();
            });
            if (error == null) {
                this.responseLog.atInfo()
                    .addKeyValue("grpcResponse", arg)
                    .log("GrpcCall responded");
            } else {
                this.responseLog.atWarn()
                    .addKeyValue("grpcResponse", arg)
                    .setCause(error)
                    .log("GrpcCall responded");
            }
        }

        static String metadataToString(Metadata metadata, Set<String> maskedHeaders, GrpcServerArgMaskingStrategy maskingStrategy) {
            var result = new StringBuilder();
            for (var key : metadata.keys()) {
                if (key.endsWith(Metadata.BINARY_HEADER_SUFFIX)) {
                    var values = metadata.getAll(Metadata.Key.of(key, Metadata.BINARY_BYTE_MARSHALLER));
                    if (values != null) {
                        for (var value : values) {
                            appendMetadata(result, key, maskedHeaders.contains(key)
                                ? maskingStrategy.mask(key, value)
                                : Base64.getEncoder().encodeToString(value));
                        }
                    }
                } else {
                    var values = metadata.getAll(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER));
                    if (values != null) {
                        for (var value : values) {
                            appendMetadata(result, key, maskedHeaders.contains(key)
                                ? maskingStrategy.mask(key, value)
                                : value);
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
