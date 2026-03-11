package io.koraframework.database.symbol.processor.extension

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import io.koraframework.database.symbol.processor.DbUtils
import io.koraframework.kora.app.ksp.extension.ExtensionResult
import io.koraframework.kora.app.ksp.extension.KoraExtension
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.TagUtils.parseTag
import io.koraframework.ksp.common.TagUtils.tagMatches
import io.koraframework.ksp.common.getOuterClassesAsPrefix

@KspExperimental
class RepositoryKoraExtension(private val kspLogger: KSPLogger) : KoraExtension {
    override fun getDependencyGenerator(resolver: Resolver, type: KSType, tag: String?): (() -> ExtensionResult)? {
        if (type.declaration !is KSClassDeclaration) {
            return null
        }
        val declaration = type.declaration as KSClassDeclaration
        if (declaration.classKind != ClassKind.INTERFACE && !(declaration.classKind == ClassKind.CLASS && Modifier.ABSTRACT in declaration.modifiers)) {
            return null
        }
        if (declaration.findAnnotation(DbUtils.repositoryAnnotation) == null) {
            return null
        }
        if (!tag.tagMatches(declaration.parseTag())) {
            return null
        }
        val repositoryName: String = declaration.getOuterClassesAsPrefix() + declaration.simpleName.asString() + "_Impl"
        return generatedByProcessorWithName(resolver, declaration, repositoryName)
    }
}
