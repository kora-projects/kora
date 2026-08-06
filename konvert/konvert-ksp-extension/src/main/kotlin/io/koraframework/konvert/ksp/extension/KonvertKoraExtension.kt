package io.koraframework.konvert.ksp.extension

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import io.koraframework.kora.app.ksp.extension.ExtensionResult
import io.koraframework.kora.app.ksp.extension.KoraExtension
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.TagUtils.parseTag

object KonvertKoraExtension : KoraExtension {

    val konverterAnnotation = ClassName("io.mcarle.konvert.api", "Konverter")
    private const val implementationSuffix = "Impl"

    override fun getDependencyGenerator(resolver: Resolver, type: KSType, tag: String?): (() -> ExtensionResult)? {
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
        if (declaration.parseTag() != tag) {
            return null
        }
        val packageName = declaration.packageName.asString()
        val expectedName = getKonverterImplName(declaration)
        val implClassName = ClassName(packageName, expectedName)
        return {
            val implementation = resolver.getClassDeclarationByName("$packageName.$expectedName")
            if (implementation == null) {
                throw io.koraframework.ksp.common.exception.ProcessingErrorException(
                    """
                    Generated Konvert implementation was not found:
                      expected type: $packageName.$expectedName

                    Fix:
                      - Ensure the Konvert KSP processor is enabled and generating `Impl` objects.
                      - Compile again after fixing earlier processing errors.
                    """.trimIndent(),
                    declaration
                )
            } else {
                ExtensionResult.CodeBlockResult(
                    declaration,
                    { CodeBlock.of("%T", implClassName) },
                    type,
                    tag,
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
