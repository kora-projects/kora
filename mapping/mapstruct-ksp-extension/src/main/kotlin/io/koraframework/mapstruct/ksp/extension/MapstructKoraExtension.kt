package io.koraframework.mapstruct.ksp.extension

import com.google.devtools.ksp.closestClassDeclaration
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ksp.toClassName
import io.koraframework.kora.app.ksp.extension.ExtensionResult
import io.koraframework.kora.app.ksp.extension.KoraExtension
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.KspCommonUtils.fixPlatformType
import io.koraframework.ksp.common.TagUtils.parseTag

object MapstructKoraExtension : KoraExtension {

    val mapperAnnotation = ClassName("org.mapstruct", "Mapper")
    private const val implementationSuffix = "Impl"

    override fun getDependencyGenerator(resolver: Resolver, type: KSType, tags: String?): (() -> ExtensionResult)? {
        val declaration = type.declaration
        if (declaration !is KSClassDeclaration) {
            return null
        }
        if (declaration.classKind != ClassKind.INTERFACE && declaration.classKind != ClassKind.CLASS) {
            return null
        }
        val annotation = declaration.findAnnotation(mapperAnnotation)
        if (annotation == null) {
            return null
        }
        val tag = declaration.parseTag()
        if (tag != tags) {
            return null
        }
        val expectedName = getMapstructMapperName(declaration)
        val generator = generatedByProcessorWithName(resolver, declaration, expectedName) ?: return null
        return {
            val result = generator() as ExtensionResult.GeneratedResult
            val constructor = result.constructor
            ExtensionResult.CodeBlockResult(
                constructor,
                { CodeBlock.of("%T(%L)", constructor.closestClassDeclaration()!!.toClassName(), it) },
                result.type.returnType!!,
                tag,
                result.type.parameterTypes.map { it!!.fixPlatformType(resolver) },
                constructor.parameters.map { it.parseTag() },
            )
        }
    }

    private fun getMapstructMapperName(declaration: KSDeclaration): String {
        val parts = mutableListOf<String>()
        parts.add(declaration.simpleName.asString())
        var parent = declaration.parentDeclaration
        while (parent != null && parent is KSClassDeclaration) {
            parts.add(parent.simpleName.asString())
            parent = parent.parentDeclaration
        }

        parts.reverse()
        return parts.joinToString("$") + implementationSuffix
    }
}
