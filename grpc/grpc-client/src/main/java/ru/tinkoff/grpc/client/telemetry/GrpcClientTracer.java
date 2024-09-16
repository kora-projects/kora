package ru.tinkoff.grpc.client.telemetry;

import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import ru.tinkoff.kora.common.Context;

import java.net.URI;

public interface GrpcClientTracer {

    <RespT, ReqT> GrpcClientSpan callSpan(Context ctx, MethodDescriptor<ReqT, RespT> method, URI uri, ClientCall<ReqT, RespT> call, Metadata headers);

    interface GrpcClientSpan {

        void close(Exception e);

        void close(Status status, Metadata trailers);

        <RespT, ReqT> GrpcClientRequestSpan reqSpan(Context ctx, MethodDescriptor<ReqT, RespT> method, ReqT req);

        <RespT, ReqT> GrpcClientResponseSpan resSpan(Context ctx, MethodDescriptor<ReqT, RespT> method, RespT message);
    }

    interface GrpcClientRequestSpan {

        void close(Exception e);

        void close();
    }

    interface GrpcClientResponseSpan {

        void close(Exception e);

        void close();
    }
}
