package ru.tinkoff.grpc.client.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import ru.tinkoff.grpc.client.ksp.GrpcClassNames.abstractCoroutineStub
import ru.tinkoff.grpc.client.ksp.GrpcClassNames.abstractStub
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionFactory
import ru.tinkoff.kora.kora.app.ksp.extension.KoraExtension
import ru.tinkoff.kora.ksp.common.KspCommonUtils.getClassDeclarationByName

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
