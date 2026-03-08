package io.koraframework.http.client.symbol.processor.extension

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Variance
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ksp.toTypeName
import io.koraframework.http.client.symbol.processor.HttpClientClassNames
import io.koraframework.http.client.symbol.processor.HttpClientClassNames.httpClientAnnotation
import io.koraframework.http.client.symbol.processor.clientName
import io.koraframework.kora.app.ksp.KoraAppUtils.findSinglePublicConstructor
import io.koraframework.kora.app.ksp.extension.ExtensionResult
import io.koraframework.kora.app.ksp.extension.KoraExtension
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.KspCommonUtils.getClassDeclarationByName

class HttpClientKoraExtension : KoraExtension {
    override fun getDependencyGenerator(resolver: Resolver, type: KSType, tag: String?): (() -> ExtensionResult)? {
        val typeName = type.toTypeName()
        if (typeName is ParameterizedTypeName && typeName.rawType == HttpClientClassNames.httpClientResponseMapper) {
            val rsTypeName = typeName.typeArguments.first()
            val rsType = type.arguments.first().type!!.resolve()
            if (rsTypeName is ParameterizedTypeName && rsTypeName.rawType == HttpClientClassNames.httpResponseEntity) {
                val responseMapperElement = resolver.getClassDeclarationByName(HttpClientClassNames.httpClientResponseMapper)!!
                val responseEntityMapperElement = resolver.getClassDeclarationByName(HttpClientClassNames.httpClientResponseEntityMapper)!!
                val responseType = rsType.arguments.first().type!!.resolve().makeNotNullable()
                val tags = ArrayList<String?>()
                tags.add(tag)
                return {
                    val delegateType = responseMapperElement.asType(listOf(resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(responseType), Variance.INVARIANT)))
                    ExtensionResult.CodeBlockResult(
                        (responseEntityMapperElement.primaryConstructor ?: responseEntityMapperElement.findSinglePublicConstructor()),
                        { dependencies -> CodeBlock.of("%T(%L)", HttpClientClassNames.httpClientResponseEntityMapper, dependencies) },
                        type,
                        tag,
                        listOf(delegateType),
                        tags
                    )
                }
            }
            return null
        }


        if (tag != null) return null
        val declaration = type.declaration
        if (declaration !is KSClassDeclaration || declaration.classKind != ClassKind.INTERFACE) {
            return null
        }
        if (declaration.findAnnotation(httpClientAnnotation) == null) {
            return null
        }
        return generatedByProcessorWithName(resolver, declaration, declaration.clientName())
    }
}

