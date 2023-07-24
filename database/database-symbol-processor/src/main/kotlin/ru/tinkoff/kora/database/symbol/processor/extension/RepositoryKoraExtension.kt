package ru.tinkoff.kora.database.symbol.processor.extension

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import ru.tinkoff.kora.database.symbol.processor.DbUtils
import ru.tinkoff.kora.kora.app.ksp.extension.ExtensionResult
import ru.tinkoff.kora.kora.app.ksp.extension.KoraExtension
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.CommonAopUtils.hasAopAnnotations
import ru.tinkoff.kora.ksp.common.getOuterClassesAsPrefix

@KspExperimental
class RepositoryKoraExtension(private val kspLogger: KSPLogger) : KoraExtension {
    override fun getDependencyGenerator(resolver: Resolver, type: KSType, tags: Set<String>): (() -> ExtensionResult)? {
        if (tags.isNotEmpty()) return null
        if (type.declaration !is KSClassDeclaration) {
            return null
        }
        val declaration = type.declaration as KSClassDeclaration
        if (declaration.classKind != ClassKind.INTERFACE && (declaration.classKind != ClassKind.CLASS || !declaration.modifiers.contains(Modifier.ABSTRACT))) {
            return null
        }
        if (declaration.findAnnotation(DbUtils.repositoryAnnotation) == null) {
            return null
        }
        return lambda@{
            val packageName = declaration.packageName.asString()
            val repositoryName: String = declaration.getOuterClassesAsPrefix() + declaration.simpleName.asString() + "_Impl"
            val repositoryElement = resolver.getClassDeclarationByName("$packageName.$repositoryName")
            if (repositoryElement == null) {
                // annotation processor will handle it
                return@lambda ExtensionResult.RequiresCompilingResult
            }
            if (!hasAopAnnotations(resolver, repositoryElement)) {
                return@lambda repositoryElement.getConstructors().map { ExtensionResult.fromConstructor(it, repositoryElement) }.first()
            }

            val aopProxy = repositoryElement.getOuterClassesAsPrefix() + repositoryElement.simpleName.getShortName() + "__AopProxy"
            val aopProxyElement = resolver.getClassDeclarationByName("$packageName.$aopProxy")
            if (aopProxyElement == null) {
                return@lambda ExtensionResult.RequiresCompilingResult
            }
            val constructor = aopProxyElement.getConstructors().filter { it.isPublic() }.firstOrNull()
            if (constructor == null) {
                throw IllegalStateException()
            }
            ExtensionResult.fromConstructor(constructor, aopProxyElement)
        }
    }
}
