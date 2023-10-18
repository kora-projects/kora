package ru.tinkoff.grpc.client.ksp

import com.squareup.kotlinpoet.ClassName


object GrpcClassNames {
    val serviceDescriptor = ClassName("io.grpc", "ServiceDescriptor")
    val abstractStub = ClassName("io.grpc.stub", "AbstractStub")
    val abstractCoroutineStub = ClassName("io.grpc.kotlin", "AbstractCoroutineStub")
    val grpcGenerated = ClassName("io.grpc.stub.annotations", "GrpcGenerated")
    val channel = ClassName("io.grpc", "Channel")
    val managedChannelLifecycle = ClassName("ru.tinkoff.grpc.client", "ManagedChannelLifecycle")
    val grpcClientConfig = ClassName("ru.tinkoff.grpc.client.config", "GrpcClientConfig")
    val stubFor = ClassName("io.grpc.kotlin", "StubFor")

}
