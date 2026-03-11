package io.koraframework.grpc.client.ksp

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class GrpcClientStubForSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment) = GrpcClientStubForSymbolProcessor(environment)
}
