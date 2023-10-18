package ru.tinkoff.grpc.client.ksp

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment

class GrpcClientStubForSymbolProcessorProvider : com.google.devtools.ksp.processing.SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment) = GrpcClientStubForSymbolProcessor(environment)
}
