package ru.tinkoff.kora.json.ksp.extension

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getFunctionDeclarationsByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Variance
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.json.ksp.JsonTypes
import ru.tinkoff.kora.json.ksp.isNativePackage
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionResult
import ru.tinkoff.kora.kora.app.ksp.extension.KoraExtension
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.KspCommonUtils.parametrized

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
                val jsonWriterDecl = resolver.getClassDeclarationByName(JsonTypes.jsonWriter.canonicalName)!!
                val functionDecl = resolver.getFunctionDeclarationsByName("ru.tinkoff.kora.json.common.JsonKotlin.writerForNullable").first()
                val writerType = jsonWriterDecl.asType(
                    listOf(
                        resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(possibleJsonClass), Variance.INVARIANT)
                    )
                )
                val delegateType = jsonWriterDecl.asType(
                    listOf(
                        resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(possibleJsonClass.makeNotNullable()), Variance.INVARIANT)
                    )
                )
                val functionType = functionDecl.parametrized(writerType, listOf(delegateType))
                return { ExtensionResult.fromExecutable(functionDecl, functionType) }
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
                val jsonReaderDecl = resolver.getClassDeclarationByName(JsonTypes.jsonReader.canonicalName)!!
                val functionDecl = resolver.getFunctionDeclarationsByName("ru.tinkoff.kora.json.common.JsonKotlin.readerForNullable").first()
                val readerType = jsonReaderDecl.asType(
                    listOf(
                        resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(possibleJsonClass), Variance.INVARIANT)
                    )
                )
                val delegateType = jsonReaderDecl.asType(
                    listOf(
                        resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(possibleJsonClass.makeNotNullable()), Variance.INVARIANT)
                    )
                )
                val functionType = functionDecl.parametrized(readerType, listOf(delegateType))
                return { ExtensionResult.fromExecutable(functionDecl, functionType) }
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
