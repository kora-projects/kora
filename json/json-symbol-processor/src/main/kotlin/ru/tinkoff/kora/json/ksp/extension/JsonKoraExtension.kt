package ru.tinkoff.kora.json.ksp.extension

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Variance
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.json.ksp.JsonTypes
import ru.tinkoff.kora.json.ksp.isNativePackage
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionResult
import ru.tinkoff.kora.kora.app.ksp.extension.KoraExtension
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow

class JsonKoraExtension() : KoraExtension {
    override fun getDependencyGenerator(resolver: Resolver, type: KSType, tag: String?): (() -> ExtensionResult)? {
        if (tag != null) {
            return null
        }
        val tn = type.toTypeName()
        if (tn !is ParameterizedTypeName) {
            return null
        }
        if (tn.rawType == JsonTypes.jsonWriter) {
            val possibleJsonClass = type.arguments[0].type!!.resolve()
            if (tn.typeArguments.first().isNullable) {
                val jsonWriterDecl = type.declaration as KSClassDeclaration
                val delegateType = jsonWriterDecl.asType(
                    listOf(
                        resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(possibleJsonClass.makeNotNullable()), Variance.INVARIANT)
                    )
                )
                return {
                    ExtensionResult.CodeBlockResult(
                        type.declaration,
                        { args ->
                            CodeBlock.builder()
                                .add("\n")
                                .addStatement("val delegate = %L", args)
                                .controlFlow("%T<%T> { gen, o ->", JsonTypes.jsonWriter, tn.typeArguments.first()) {
                                    addStatement("delegate.write(gen, o)")
                                }
                                .build()
                        },
                        type,
                        null,
                        listOf(delegateType),
                        listOf(null)
                    )
                }
            }
            val possibleJsonClassDeclaration = possibleJsonClass.declaration
            if (possibleJsonClassDeclaration !is KSClassDeclaration) {
                return null
            }
            if (possibleJsonClassDeclaration.isAnnotationPresent(JsonTypes.json) || possibleJsonClassDeclaration.isAnnotationPresent(JsonTypes.jsonWriterAnnotation)) {
                return generatedByProcessor(resolver, possibleJsonClassDeclaration, "JsonWriter")
            }
            return null
        }
        if (tn.rawType == JsonTypes.jsonReader) {
            val possibleJsonClass = type.arguments[0].type!!.resolve()
            if (possibleJsonClass.declaration.isNativePackage()) {
                return null
            }
            if (tn.typeArguments.first().isNullable) {
                val jsonReaderDecl = type.declaration as KSClassDeclaration
                val delegateType = jsonReaderDecl.asType(
                    listOf(
                        resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(possibleJsonClass.makeNotNullable()), Variance.INVARIANT)
                    )
                )
                return {
                    ExtensionResult.CodeBlockResult(
                        type.declaration,
                        { args ->
                            CodeBlock.builder()
                                .add("\n")
                                .addStatement("val delegate = %L", args)
                                .controlFlow("%T<%T> { parser ->", JsonTypes.jsonReader, tn.typeArguments.first()) {
                                    addStatement("delegate.read(parser)")
                                }
                                .build()
                        },
                        type,
                        null,
                        listOf(delegateType),
                        listOf(null)
                    )
                }
            }
            val possibleJsonClassDeclaration = possibleJsonClass.declaration
            if (possibleJsonClassDeclaration !is KSClassDeclaration) {
                return null
            }
            if (possibleJsonClassDeclaration.isAnnotationPresent(JsonTypes.json)
                || possibleJsonClassDeclaration.isAnnotationPresent(JsonTypes.jsonReaderAnnotation)
                || possibleJsonClassDeclaration.primaryConstructor?.isAnnotationPresent(JsonTypes.jsonReaderAnnotation) == true
            ) {
                return generatedByProcessor(resolver, possibleJsonClassDeclaration, "JsonReader")
            }
            return null
        }
        return null
    }
}
