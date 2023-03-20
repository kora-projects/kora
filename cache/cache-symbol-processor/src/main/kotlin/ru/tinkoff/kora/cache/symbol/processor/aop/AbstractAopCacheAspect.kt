package ru.tinkoff.kora.cache.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Variance
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.CodeBlock
import ru.tinkoff.kora.aop.symbol.processor.KoraAspect
import ru.tinkoff.kora.cache.Cache
import ru.tinkoff.kora.cache.CacheManager
import ru.tinkoff.kora.cache.symbol.processor.CacheMeta
import ru.tinkoff.kora.cache.symbol.processor.CacheOperation
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.ksp.common.exception.ProcessingError
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import java.util.*
import java.util.stream.Collectors
import javax.tools.Diagnostic

@KspExperimental
abstract class AbstractAopCacheAspect : KoraAspect {

    data class CacheMirrors(val manager: KSType, val cache: KSType)

    open fun getCacheMirrors(operation: CacheOperation, method: KSFunctionDeclaration, resolver: Resolver): CacheMirrors {
        val keyElement = resolver.getClassDeclarationByName(resolver.getKSNameFromString(operation.key.canonicalName()))
            ?: throw ProcessingErrorException(
                ProcessingError(
                    "Cache Key is not yet generated, will try next round...",
                    method,
                    Diagnostic.Kind.WARNING,
                )
            )

        if (operation.value.canonicalName == null) {
            throw ProcessingErrorException(
                ProcessingError(
                    "Cache Return type is not yet known, will try next round...",
                    method,
                    Diagnostic.Kind.WARNING,
                )
            )
        }

        val valueElement = resolver.getClassDeclarationByName(resolver.getKSNameFromString(operation.value.canonicalName))
            ?: throw ProcessingErrorException(
                ProcessingError(
                    "Cache Return type is not yet known, will try next round...",
                    method,
                    Diagnostic.Kind.NOTE,
                )
            )

        val managerType = resolver.getClassDeclarationByName(CacheManager::class.java.canonicalName)?.asType(
            listOf(
                resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(keyElement.asStarProjectedType()), Variance.INVARIANT),
                resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(valueElement.asStarProjectedType()), Variance.INVARIANT),
            )
        ) ?: throw IllegalStateException("Can't extract declaration for: ${CacheManager::class.java.canonicalName}")

        val cacheType = resolver.getClassDeclarationByName(Cache::class.java.canonicalName)?.asType(
            listOf(
                resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(keyElement.asStarProjectedType()), Variance.INVARIANT),
                resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(valueElement.asStarProjectedType()), Variance.INVARIANT),
            )
        ) ?: throw IllegalStateException("Can't extract declaration for: ${Cache::class.java.canonicalName}")

        return CacheMirrors(managerType, cacheType)
    }

    open fun getCacheFields(
        operation: CacheOperation,
        mirror: CacheMirrors,
        aspectContext: KoraAspect.AspectContext
    ): List<String> {
        val cacheFields = ArrayList<String>()
        for (manager in operation.meta.managers) {
            val managerTags: List<AnnotationSpec>
            if (manager.tags.isEmpty()) {
                managerTags = listOf()
            } else if (manager.tags.size == 1) {
                managerTags = listOf(
                    AnnotationSpec.builder(Tag::class.java)
                        .addMember("value", manager.tags[0])
                        .build()
                )
            } else {
                val tagValue: String = manager.tags.stream()
                    .collect(Collectors.joining(", ", "{", "}"))

                managerTags = listOf(
                    AnnotationSpec.builder(Tag::class.java)
                        .addMember("value", tagValue)
                        .build()
                )
            }

            val fieldManager = aspectContext.fieldFactory.constructorParam(mirror.manager, managerTags)
            val cacheField = aspectContext.fieldFactory.constructorInitialized(mirror.cache,
                CodeBlock.of("%L.getCache(\"%L\")", fieldManager, manager.name))

            cacheFields.add(cacheField)
        }

        return cacheFields
    }

    open fun getKeyRecordParameters(operation: CacheOperation, method: KSFunctionDeclaration): String {
        return operation.meta.getParametersNames(method).joinToString(", ")
    }

    open fun getSuperMethod(method: KSFunctionDeclaration, superCall: String): String {
        return method.parameters.joinToString(", ", "$superCall(", ")")
    }

    open fun getCacheParameters(operation: CacheOperation, fieldManagers: List<String>): String {
        val joiner = StringJoiner(", ")
        for (i in fieldManagers.indices) {
            val fieldManager = fieldManagers[i]
            val manager: CacheMeta.Manager = operation.meta.managers[i]
            joiner.add(fieldManager + ".getCache(\"" + manager.name + "\")")
        }
        return joiner.toString()
    }
}
