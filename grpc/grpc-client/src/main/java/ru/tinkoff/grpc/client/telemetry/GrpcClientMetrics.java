package ru.tinkoff.grpc.client.telemetry;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;

public interface GrpcClientMetrics {
    <RespT, ReqT> void recordEnd(MethodDescriptor<ReqT, RespT> method, long startTime, Exception e);

    <RespT, ReqT> void recordEnd(MethodDescriptor<ReqT, RespT> method, long startTime, Status status, Metadata trailers);

    <RespT, ReqT> void recordSendMessage(MethodDescriptor<ReqT, RespT> method, ReqT message);

    <RespT, ReqT> void recordReceiveMessage(MethodDescriptor<ReqT, RespT> method, RespT message);
}
