package ru.tinkoff.kora.grpc.telemetry;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.grpc.GrpcServer;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;

import javax.annotation.Nullable;

public final class Slf4jGrpcServerLogger implements GrpcServerLogger {

    private static final Logger log = LoggerFactory.getLogger(GrpcServer.class);

    @Override
    public boolean isEnabled() {
        return log.isInfoEnabled();
    }

    @Override
    public void logEnd(String serviceName, String methodName, @Nullable Status status, @Nullable Throwable exception, long processingTime) {
        var marker = StructuredArgument.marker("grpcResponse", gen -> {
            gen.writeStartObject();
            gen.writeStringField("serviceName", serviceName);
            gen.writeStringField("operation", serviceName + "/" + methodName);
            gen.writeNumberField("processingTime", processingTime / 1_000_000);
            if (status != null) {
                gen.writeStringField("status", status.getCode().name());
            }
            if (exception != null) {
                var exceptionType = exception.getClass().getCanonicalName();
                gen.writeStringField("exceptionType", exceptionType);
            }
            gen.writeEndObject();
        });

        if (status != null && status.isOk()) {
            log.info(marker, "GrpcCall responded {} for {}#{}", status, serviceName, methodName);
        } else if (status != null) {
            log.warn(marker, "GrpcCall responded {} for {}#{}", status, serviceName, methodName, exception);
        } else {
            log.warn(marker, "GrpcCall responded for {}#{}", serviceName, methodName, exception);
        }
    }

    @Override
    public void logBegin(ServerCall<?, ?> call, Metadata headers, String serviceName, String methodName) {
        var marker = StructuredArgument.marker("grpcRequest", gen -> {
            gen.writeStartObject();
            gen.writeStringField("serviceName", serviceName);
            gen.writeStringField("operation", serviceName + "/" + methodName);
            gen.writeEndObject();
        });

        if (log.isDebugEnabled()) {
            log.debug(marker, "GrpcCall received for {}#{}\n{}", serviceName, methodName, headers);
        } else {
            log.info(marker, "GrpcCall received for {}#{}", serviceName, methodName);
        }
    }

    @Override
    public void logSendMessage(String serviceName, String methodName, Object message) {

    }

    @Override
    public void logReceiveMessage(String serviceName, String methodName, Object message) {

    }
}
