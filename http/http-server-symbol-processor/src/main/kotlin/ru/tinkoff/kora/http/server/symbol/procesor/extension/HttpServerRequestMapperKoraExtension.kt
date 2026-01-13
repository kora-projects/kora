package ru.tinkoff.kora.http.server.symbol.procesor.extension

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Variance
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.http.server.symbol.procesor.HttpServerClassNames
import ru.tinkoff.kora.kora.app.ksp.KoraAppUtils.findSinglePublicConstructor
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionResult
import ru.tinkoff.kora.kora.app.ksp.extension.KoraExtension
import ru.tinkoff.kora.ksp.common.KspCommonUtils.getClassDeclarationByName


class HttpServerRequestMapperKoraExtension : KoraExtension {
    override fun getDependencyGenerator(resolver: Resolver, type: KSType, tag: String?): (() -> ExtensionResult)? {
        val typeName = type.toTypeName()
        if (typeName !is ParameterizedTypeName) {
            return null
        }
        if (typeName.rawType != HttpServerClassNames.httpServerResponseMapper) {
            return null
        }
        val rsTypeName = typeName.typeArguments.first()
        val rsType = type.arguments.first().type!!.resolve()
        if (rsTypeName is ParameterizedTypeName && rsTypeName.rawType == HttpServerClassNames.httpResponseEntity) {
            val responseMapperElement = resolver.getClassDeclarationByName(HttpServerClassNames.httpServerResponseMapper)!!
            val responseEntityMapperElement = resolver.getClassDeclarationByName(HttpServerClassNames.httpServerResponseEntityMapper)!!
            val responseType = rsType.arguments.first().type!!.resolve().makeNotNullable()
            val tags = ArrayList<String?>()
            tags.add(tag)
            return {
                val delegateType = responseMapperElement.asType(listOf(resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(responseType), Variance.INVARIANT)))
                ExtensionResult.CodeBlockResult(
                    (responseEntityMapperElement.primaryConstructor ?: responseEntityMapperElement.findSinglePublicConstructor()),
                    { dependencies -> CodeBlock.of("%T(%L)", HttpServerClassNames.httpServerResponseEntityMapper, dependencies) },
                    type,
                    tag,
                    listOf(delegateType),
                    tags
                )
            }
        }
        return null
    }
}
