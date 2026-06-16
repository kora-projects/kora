package io.koraframework.grpc.client.telemetry.impl;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.koraframework.logging.common.arg.StructuredArgument;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class DefaultGrpcClientLoggerFactory {

    public static final DefaultGrpcClientLoggerFactory INSTANCE = new DefaultGrpcClientLoggerFactory();

    public DefaultGrpcClientLogger create(DefaultGrpcClientTelemetry.TelemetryContext context) {
        var requestLog = LoggerFactory.getLogger(context.service().getName() + ".request");
        var responseLog = LoggerFactory.getLogger(context.service().getName() + ".response");
        return new DefaultGrpcClientLogger(requestLog, responseLog, context);
    }

    public static class DefaultGrpcClientLogger {

        protected final Logger requestLog;
        protected final Logger responseLog;
        protected final DefaultGrpcClientTelemetry.TelemetryContext context;

        public DefaultGrpcClientLogger(Logger requestLog,
                                       Logger responseLog,
                                       DefaultGrpcClientTelemetry.TelemetryContext context) {
            this.requestLog = requestLog;
            this.responseLog = responseLog;
            this.context = context;
        }

        public void logRequest(MethodDescriptor<?, ?> method, Metadata requestHeaders) {
            if (!this.requestLog.isInfoEnabled()) {
                return;
            }
            var headers = this.requestLog.isDebugEnabled() ? requestHeaders.toString() : null;
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
    }
}
