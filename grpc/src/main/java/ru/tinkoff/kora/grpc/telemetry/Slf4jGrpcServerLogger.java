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
    public void logEnd(String serviceName, String methodName, @Nullable Status status, @Nullable Throwable exception, long processingTime) {
        if (status != null && status.isOk()) {
            var marker = StructuredArgument.marker("grpcResponse", gen -> {
                gen.writeStartObject();
                gen.writeStringField("serviceName", serviceName);
                gen.writeStringField("operation", serviceName + "/" + methodName);
                gen.writeNumberField("processingTime", processingTime / 1_000_000);
                gen.writeStringField("status", status.getCode().name());
                gen.writeEndObject();
            });
            log.info(marker, "Response finished");
            return;
        }
        if (status == null) {
            var marker = StructuredArgument.marker("grpcResponse", gen -> {
                gen.writeStartObject();
                gen.writeStringField("serviceName", serviceName);
                gen.writeStringField("operation", serviceName + "/" + methodName);
                gen.writeNumberField("processingTime", processingTime / 1_000_000);
                gen.writeNullField("status");
                gen.writeEndObject();
            });
            log.warn(marker, "Response finished", exception);
        } else {
            var marker = StructuredArgument.marker("grpcResponse", gen -> {
                gen.writeStartObject();
                gen.writeStringField("serviceName", serviceName);
                gen.writeStringField("operation", serviceName + "/" + methodName);
                gen.writeNumberField("processingTime", processingTime / 1_000_000);
                gen.writeStringField("status", status.getCode().name());
                gen.writeEndObject();
            });
            log.warn(marker, "Response finished", exception);
        }
    }

    @Override
    public void logBegin(ServerCall<?, ?> call, Metadata headers, String serviceName, String methodName) {

    }

    @Override
    public void logSendMessage(String serviceName, String methodName, Object message) {

    }

    @Override
    public void logReceiveMessage(String serviceName, String methodName, Object message) {

    }
}
