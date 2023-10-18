package ru.tinkoff.grpc.client.telemetry;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import ru.tinkoff.kora.common.Context;

import java.net.URI;

public interface GrpcClientLogger {
    boolean enabled();

    <RespT, ReqT> void logCall(Context ctx, MethodDescriptor<ReqT, RespT> method, URI uri);

    <RespT, ReqT> void logEnd(MethodDescriptor<ReqT, RespT> method, long start, Exception e);

    <RespT, ReqT> void logEnd(MethodDescriptor<ReqT, RespT> method, long start, Status status, Metadata trailers);

    <RespT, ReqT> void logSendMessage(MethodDescriptor<ReqT, RespT> method, ReqT message);

    <RespT, ReqT> void logReceiveMessage(MethodDescriptor<ReqT, RespT> method, RespT message);
}
