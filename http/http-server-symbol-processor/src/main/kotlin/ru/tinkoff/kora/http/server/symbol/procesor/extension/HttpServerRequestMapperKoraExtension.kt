package ru.tinkoff.kora.http.server.symbol.procesor.extension

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getFunctionDeclarationsByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Variance
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.http.server.symbol.procesor.HttpServerClassNames.httpServerRequestMapper
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionResult
import ru.tinkoff.kora.kora.app.ksp.extension.KoraExtension
import ru.tinkoff.kora.ksp.common.KspCommonUtils.parametrized
import java.util.concurrent.CompletionStage

class HttpServerRequestMapperKoraExtension : KoraExtension {
    override fun getDependencyGenerator(resolver: Resolver, type: KSType, tags: Set<String>): (() -> ExtensionResult)? {
        if (tags.isNotEmpty()) {
            return null
        }
        if (type.toClassName() != httpServerRequestMapper) {
            return null
        }
        val argType = type.arguments[0].type!!
        val arg = argType.toTypeName()
        if (arg is ParameterizedTypeName && arg.rawType == CompletionStage::class.asClassName()) {
            return null
        }

        val requestMapperDecl = resolver.getClassDeclarationByName(httpServerRequestMapper.canonicalName)!!
        val completionStageDecl = resolver.getClassDeclarationByName(CompletionStage::class.qualifiedName!!)!!

        val completionStageType = completionStageDecl.asType(listOf(
            resolver.getTypeArgument(argType, Variance.INVARIANT)
        ))
        val asyncMapperType = requestMapperDecl.asType(
            listOf(
                resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(completionStageType), Variance.INVARIANT)
            )
        )

        val functionDecl = resolver.getFunctionDeclarationsByName(httpServerRequestMapper.canonicalName + ".fromAsync").first()
        val functionType = functionDecl.parametrized(type, listOf(asyncMapperType))
        return {
            ExtensionResult.fromExecutable(functionDecl, functionType)
        }
    }
}
