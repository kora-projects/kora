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
import io.koraframework.ksp.common.TagUtils.parseTag

class HttpClientKoraExtension : KoraExtension {
    override fun getDependencyGenerator(resolver: Resolver, type: KSType, tag: String?): (() -> ExtensionResult)? {
        val typeName = type.toTypeName()
        if (typeName is ParameterizedTypeName && typeName.rawType == HttpClientClassNames.httpClientResponseMapper) {
            val rsTypeName = typeName.typeArguments.first()
            val rsType = type.arguments.first().type!!.resolve()
            if (rsTypeName is ParameterizedTypeName && rsTypeName.rawType == HttpClientClassNames.either) {
                val responseMapperElement = resolver.getClassDeclarationByName(HttpClientClassNames.httpClientResponseMapper)!!
                val eitherMapperElement = resolver.getClassDeclarationByName(HttpClientClassNames.httpClientEitherResponseMapper)!!
                val successType = rsType.arguments[0].type!!.resolve().makeNotNullable()
                val errorType = rsType.arguments[1].type!!.resolve().makeNotNullable()
                val successTag = tag ?: successType.parseTag()
                val errorTag = tag ?: errorType.parseTag()
                return {
                    val successMapperType = responseMapperElement.asStarProjectedType().replace(listOf(resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(successType), Variance.INVARIANT)))
                    val errorMapperType = responseMapperElement.asStarProjectedType().replace(listOf(resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(errorType), Variance.INVARIANT)))
                    ExtensionResult.CodeBlockResult(
                        (eitherMapperElement.primaryConstructor ?: eitherMapperElement.findSinglePublicConstructor()),
                        { dependencies -> CodeBlock.of("%T(%L)", HttpClientClassNames.httpClientEitherResponseMapper, dependencies) },
                        type,
                        tag,
                        listOf(successMapperType, errorMapperType),
                        listOf(successTag, errorTag)
                    )
                }
            }
            if (rsTypeName is ParameterizedTypeName && rsTypeName.rawType == HttpClientClassNames.httpResponseEntity) {
                val responseMapperElement = resolver.getClassDeclarationByName(HttpClientClassNames.httpClientResponseMapper)!!
                val responseEntityMapperElement = resolver.getClassDeclarationByName(HttpClientClassNames.httpClientResponseEntityMapper)!!
                val responseType = rsType.arguments.first().type!!.resolve().makeNotNullable()
                val responseTypeName = rsTypeName.typeArguments.first()
                if (responseTypeName is ParameterizedTypeName && responseTypeName.rawType == HttpClientClassNames.either) {
                    val successType = responseType.arguments[0].type!!.resolve().makeNotNullable()
                    val errorType = responseType.arguments[1].type!!.resolve().makeNotNullable()
                    val successTag = tag ?: successType.parseTag()
                    val errorTag = tag ?: errorType.parseTag()
                    return {
                        val successMapperType = responseMapperElement.asStarProjectedType().replace(listOf(resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(successType), Variance.INVARIANT)))
                        val errorMapperType = responseMapperElement.asStarProjectedType().replace(listOf(resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(errorType), Variance.INVARIANT)))
                        ExtensionResult.CodeBlockResult(
                            (responseEntityMapperElement.primaryConstructor ?: responseEntityMapperElement.findSinglePublicConstructor()),
                            { dependencies -> CodeBlock.of("%T(%T(%L))", HttpClientClassNames.httpClientResponseEntityMapper, HttpClientClassNames.httpClientEitherResponseMapper, dependencies) },
                            type,
                            tag,
                            listOf(successMapperType, errorMapperType),
                            listOf(successTag, errorTag)
                        )
                    }
                }
                val tags = ArrayList<String?>()
                tags.add(tag)
                return {
                    val delegateType = responseMapperElement.asStarProjectedType().replace(listOf(resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(responseType), Variance.INVARIANT)))
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
            if (tag == HttpClientClassNames.json.canonicalName) {
                val jsonReaderElement = resolver.getClassDeclarationByName(HttpClientClassNames.jsonReader)!!
                val jsonMapperElement = resolver.getClassDeclarationByName(HttpClientClassNames.jsonHttpClientResponseMapper)!!
                return {
                    val jsonReaderType = jsonReaderElement.asStarProjectedType().replace(listOf(resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(rsType), Variance.INVARIANT)))
                    ExtensionResult.CodeBlockResult(
                        (jsonMapperElement.primaryConstructor ?: jsonMapperElement.findSinglePublicConstructor()),
                        { dependencies -> CodeBlock.of("%T(%L)", HttpClientClassNames.jsonHttpClientResponseMapper, dependencies) },
                        type,
                        tag,
                        listOf(jsonReaderType),
                        listOf(null)
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
