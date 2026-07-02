package ru.tinkoff.kora.konvert.ksp.extension

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionResult
import ru.tinkoff.kora.kora.app.ksp.extension.KoraExtension
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.TagUtils.parseTags

object KonvertKoraExtension : KoraExtension {

    val konverterAnnotation = ClassName("io.mcarle.konvert.api", "Konverter")
    private const val implementationSuffix = "Impl"

    override fun getDependencyGenerator(resolver: Resolver, type: KSType, tags: Set<String>): (() -> ExtensionResult)? {
        val declaration = type.declaration
        if (declaration !is KSClassDeclaration) {
            return null
        }
        if (declaration.classKind != ClassKind.INTERFACE) {
            return null
        }
        val annotation = declaration.findAnnotation(konverterAnnotation)
        if (annotation == null) {
            return null
        }
        val tag = declaration.parseTags()
        if (tag != tags) {
            return null
        }
        val packageName = declaration.packageName.asString()
        val expectedName = getKonverterImplName(declaration)
        val implClassName = ClassName(packageName, expectedName)
        return {
            val implementation = resolver.getClassDeclarationByName("$packageName.$expectedName")
            if (implementation == null) {
                ExtensionResult.RequiresCompilingResult
            } else {
                ExtensionResult.CodeBlockResult(
                    declaration,
                    { CodeBlock.of("%T", implClassName) },
                    type,
                    tags,
                    emptyList(),
                    emptyList()
                )
            }
        }
    }

    private fun getKonverterImplName(declaration: KSDeclaration): String {
        // Konvert always generates a top-level `object <SimpleName>Impl` in the same package,
        // even for a nested @Konverter interface (the enclosing type name is dropped).
        return declaration.simpleName.asString() + implementationSuffix
    }
}
