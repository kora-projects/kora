package io.koraframework.grpc.client.annotation.processor;

import com.palantir.javapoet.ClassName;

public class GrpcClassNames {
    public static final ClassName serviceDescriptor = ClassName.get("io.grpc", "ServiceDescriptor");
    public static final ClassName abstractStub = ClassName.get("io.grpc.stub", "AbstractStub");
    public static final ClassName grpcGenerated = ClassName.get("io.grpc.stub.annotations", "GrpcGenerated");
    public static final ClassName channel = ClassName.get("io.grpc", "Channel");
    public static final ClassName managedChannelLifecycle = ClassName.get("io.koraframework.grpc.client", "ManagedChannelLifecycle");
    public static final ClassName grpcClientConfig = ClassName.get("io.koraframework.grpc.client.config", "GrpcClientConfig");
}
