package ru.tinkoff.kora.config.ksp.extension

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Nullability
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ksp.toTypeName
import ru.tinkoff.kora.config.ksp.ConfigClassNames
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionResult
import ru.tinkoff.kora.kora.app.ksp.extension.KoraExtension
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.CommonClassNames

class ConfigKoraExtension() : KoraExtension {
    override fun getDependencyGenerator(resolver: Resolver, type: KSType, tags: Set<String>): (() -> ExtensionResult)? {
        if (tags.isNotEmpty()) return null
        val actualType = if (type.nullability == Nullability.PLATFORM) type.makeNotNullable() else type
        actualType.toTypeName().let {
            if (it is ParameterizedTypeName && it.rawType == CommonClassNames.configValueExtractor) {
                val configTypeDecl = actualType.arguments.first().type?.resolve()?.declaration
                if (configTypeDecl?.isAnnotationPresent(ConfigClassNames.configSourceAnnotation) == true || configTypeDecl?.isAnnotationPresent(ConfigClassNames.configValueExtractorAnnotation) == true) {
                    return generatedByProcessor(resolver, configTypeDecl as KSClassDeclaration, ConfigClassNames.configValueExtractor.simpleName)
                }
            }
        }
        return null
    }
}
