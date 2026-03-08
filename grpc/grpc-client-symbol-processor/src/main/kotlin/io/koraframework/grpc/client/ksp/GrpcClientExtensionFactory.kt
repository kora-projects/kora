package io.koraframework.grpc.client.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import io.koraframework.grpc.client.ksp.GrpcClassNames.abstractCoroutineStub
import io.koraframework.grpc.client.ksp.GrpcClassNames.abstractStub
import io.koraframework.kora.app.ksp.extension.ExtensionFactory
import io.koraframework.kora.app.ksp.extension.KoraExtension
import io.koraframework.ksp.common.KspCommonUtils.getClassDeclarationByName

class GrpcClientExtensionFactory : ExtensionFactory {
    override fun create(resolver: Resolver, kspLogger: KSPLogger, codeGenerator: CodeGenerator): KoraExtension? {
        val abstractStub = resolver.getClassDeclarationByName(abstractStub)
        val abstractCoroutineStub = resolver.getClassDeclarationByName(abstractCoroutineStub)
        if (abstractStub == null && abstractCoroutineStub == null) {
            return null
        }
        return GrpcClientExtension(resolver, kspLogger, codeGenerator, abstractStub, abstractCoroutineStub)
    }
}
