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
        return new DefaultGrpcServerLogger(requestLog, responseLog);
    }

    public static class DefaultGrpcServerLogger {

        protected final Logger requestLog;
        protected final Logger responseLog;

        public DefaultGrpcServerLogger(Logger requestLog, Logger responseLog) {
            this.requestLog = requestLog;
            this.responseLog = responseLog;
        }

        public void logRequest(String service, String method, Metadata requestHeaders) {
            if (!this.requestLog.isInfoEnabled()) {
                return;
            }
            var headers = this.requestLog.isDebugEnabled() ? requestHeaders.toString() : null;
            this.requestLog.atInfo()
                .addKeyValue("grpcRequest", StructuredArgument.value(gen -> {
                    gen.writeStartObject();
                    gen.writeStringProperty("serviceName", service);
                    gen.writeStringProperty("operation", service + "/" + method);
                    if (headers != null) {
                        gen.writeStringProperty("headers", headers);
                    }
                    gen.writeEndObject();
                }))
                .log("GrpcCall received");
        }

        public void logResponse(String service, String method, @Nullable Status status, @Nullable Throwable error, long processingTimeNanos) {
            if (error == null && !this.responseLog.isInfoEnabled()) {
                return;
            }
            if (error != null && !this.responseLog.isWarnEnabled()) {
                return;
            }
            var statusCode = status == null ? Status.Code.UNKNOWN : status.getCode();
            var exceptionType = error == null ? null : error.getClass().getCanonicalName();
            var arg = StructuredArgument.value(gen -> {
                gen.writeStartObject();
                gen.writeStringProperty("serviceName", service);
                gen.writeStringProperty("operation", service + "/" + method);
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
