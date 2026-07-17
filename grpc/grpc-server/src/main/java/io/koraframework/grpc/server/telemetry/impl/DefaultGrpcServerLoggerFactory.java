package io.koraframework.grpc.server.telemetry.impl;

import io.grpc.Metadata;
import io.grpc.Status;
import io.koraframework.grpc.server.GrpcServer;
import io.koraframework.logging.common.arg.StructuredArgument;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultGrpcServerLoggerFactory {

    public static final DefaultGrpcServerLoggerFactory INSTANCE = new DefaultGrpcServerLoggerFactory();

    public DefaultGrpcServerLogger create(DefaultGrpcServerTelemetry.TelemetryContext context) {
        var requestLog = LoggerFactory.getLogger(GrpcServer.class.getCanonicalName() + ".request");
        var responseLog = LoggerFactory.getLogger(GrpcServer.class.getCanonicalName() + ".response");
        return new DefaultGrpcServerLogger(context, requestLog, responseLog);
    }

    public static class DefaultGrpcServerLogger {

        protected final DefaultGrpcServerTelemetry.TelemetryContext context;
        protected final Logger requestLog;
        protected final Logger responseLog;

        public DefaultGrpcServerLogger(DefaultGrpcServerTelemetry.TelemetryContext context, Logger requestLog, Logger responseLog) {
            this.context = context;
            this.requestLog = requestLog;
            this.responseLog = responseLog;
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
            var headers = this.requestLog.isDebugEnabled() ? requestHeaders.toString() : null;
            var body = this.requestLog.isTraceEnabled() && requestMessage != null
                ? this.context.bodyConverter().convertRequestMessage(requestMessage)
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
    }
}
