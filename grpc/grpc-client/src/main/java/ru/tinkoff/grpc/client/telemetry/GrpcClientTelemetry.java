package ru.tinkoff.grpc.client.telemetry;

import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import ru.tinkoff.kora.common.Context;

public interface GrpcClientTelemetry {
    <ReqT, RespT> GrpcClientTelemetryCtx<ReqT, RespT> get(Context ctx, MethodDescriptor<ReqT, RespT> method, ClientCall<ReqT, RespT> call, Metadata headers);

    interface GrpcClientTelemetryCtx<ReqT, RespT> {

        void close(Status status, Metadata trailers);

        void close(Exception e);

        GrpcClientSendMessageTelemetryCtx<ReqT, RespT> sendMessage(ReqT message);

        GrpcClientReceiveMessageTelemetryCtx<ReqT, RespT> receiveMessage(RespT message);
    }

    interface GrpcClientSendMessageTelemetryCtx<ReqT, RespT> {

        void close(Exception e);

        void close();
    }

    interface GrpcClientReceiveMessageTelemetryCtx<ReqT, RespT> {

        void close(Exception e);

        void close();
    }
}
