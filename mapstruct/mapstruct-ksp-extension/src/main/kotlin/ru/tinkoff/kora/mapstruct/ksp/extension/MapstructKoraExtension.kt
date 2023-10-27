package ru.tinkoff.kora.mapstruct.ksp.extension

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionResult
import ru.tinkoff.kora.kora.app.ksp.extension.KoraExtension
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.TagUtils.parseTags

object MapstructKoraExtension : KoraExtension {

    val mapperAnnotation = ClassName("org.mapstruct", "Mapper")
    private const val implementationSuffix = "Impl"

    override fun getDependencyGenerator(resolver: Resolver, type: KSType, tags: Set<String>): (() -> ExtensionResult)? {
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
        val tag = declaration.parseTags()
        if (tag != tags) {
            return null
        }
        val packageName = declaration.packageName.asString()
        val expectedName = getMapstructMapperName(declaration)
        return {
            val implementation = resolver.getClassDeclarationByName("$packageName.$expectedName")
            if (implementation == null) {
                ExtensionResult.RequiresCompilingResult
            } else {
                val constructor = implementation.getConstructors().first()
                ExtensionResult.fromConstructor(constructor, implementation)
            }
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
