package ru.tinkoff.kora.cache.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import ru.tinkoff.kora.aop.symbol.processor.KoraAspect
import ru.tinkoff.kora.cache.symbol.processor.CacheOperation
import ru.tinkoff.kora.cache.symbol.processor.CacheOperationUtils.Companion.getCacheOperation
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.FunctionUtils.isFlux
import ru.tinkoff.kora.ksp.common.FunctionUtils.isCompletionStage
import ru.tinkoff.kora.ksp.common.FunctionUtils.isFuture
import ru.tinkoff.kora.ksp.common.FunctionUtils.isMono
import ru.tinkoff.kora.ksp.common.FunctionUtils.isSuspend
import ru.tinkoff.kora.ksp.common.FunctionUtils.isVoid
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import java.util.concurrent.CompletionStage
import java.util.concurrent.Future

@KspExperimental
class CacheableAopKoraAspect(private val resolver: Resolver) : AbstractAopCacheAspect() {

    private val ANNOTATION_CACHEABLE = ClassName("ru.tinkoff.kora.cache.annotation", "Cacheable")
    private val ANNOTATION_CACHEABLES = ClassName("ru.tinkoff.kora.cache.annotation", "Cacheables")

    override fun getSupportedAnnotationTypes(): Set<String> {
        return setOf(ANNOTATION_CACHEABLE.canonicalName, ANNOTATION_CACHEABLES.canonicalName)
    }

    override fun apply(method: KSFunctionDeclaration, superCall: String, aspectContext: KoraAspect.AspectContext): KoraAspect.ApplyResult {
        if (method.isFuture()) {
            throw ProcessingErrorException("@Cacheable can't be applied for types assignable from ${Future::class.java}", method)
        } else if (method.isCompletionStage()) {
            throw ProcessingErrorException("@Cacheable can't be applied for types assignable from ${CompletionStage::class.java}", method)
        } else if (method.isMono()) {
            throw ProcessingErrorException("@Cacheable can't be applied for types assignable from ${CommonClassNames.mono}", method)
        } else if (method.isFlux()) {
            throw ProcessingErrorException("@Cacheable can't be applied for types assignable from ${CommonClassNames.flux}", method)
        } else if (method.isVoid()) {
            throw ProcessingErrorException("@Cacheable can't be applied for types assignable from ${Void::class}", method)
        }

        val operation = getCacheOperation(method, resolver, aspectContext)
        val body = if (method.isSuspend()) {
            buildBodySync(method, operation, superCall, resolver)
        } else {
            buildBodySync(method, operation, superCall, resolver)
        }

        return KoraAspect.ApplyResult.MethodBody(body)
    }

    private fun buildBodySync(
        method: KSFunctionDeclaration,
        operation: CacheOperation,
        superCall: String,
        resolver: Resolver
    ): CodeBlock {
        val superMethod = getSuperMethod(method, superCall)
        val builder = CodeBlock.builder()

        if (!method.isSuspend() && operation.executions.size == 1) {
            val keyBlock = CodeBlock.of("val _key = %L\n", operation.executions[0].cacheKey!!.code)
            val isSingleNullableParam = operation.executions[0].type.isMarkedNullable
            val codeBlock = if (isSingleNullableParam) {
                CodeBlock.of(
                    """
                        return if (_key != null) {
                            %L.computeIfAbsent(_key) { %L }
                        } else {
                            %L
                        }
                    """.trimIndent(), operation.executions[0].field, superMethod, superMethod
                )
            } else {
                CodeBlock.of("return %L.computeIfAbsent(_key) { %L }", operation.executions[0].field, superMethod)
            }

            return CodeBlock.builder()
                .add(keyBlock)
                .add(codeBlock)
                .build()
        }

        // cache get
        for (i in operation.executions.indices) {
            val cache = operation.executions[i]

            val keyField = "_key${i + 1}"
            val keyBlock = CodeBlock.of("val %L = %L\n", keyField, cache.cacheKey!!.code)
            builder.add(keyBlock)

            val prefix = if (i == 0) "var _value = " else "_value = "
            if (cache.cacheKey.type.type!!.resolve().isMarkedNullable) {
                builder.add(prefix)
                    .add("%L?.let { %L.get(it) }\n", keyField, cache.field)
                    .add("if(_value != null) {\n")
            } else {
                builder.add(prefix)
                    .add("%L.get(%L)\n", cache.field, keyField)
                    .add("if(_value != null) {\n")
            }

            for (j in 0 until i) {
                val prevCache = operation.executions[j]
                val keyField = "_key${j + 1}"
                builder.add("\t%L.put(%L, _value)\n", prevCache.field, keyField)
            }

            builder
                .add("\treturn _value\n")
                .add("}\n\n")
        }

        // cache super method
        builder.add("_value = %L\n", superMethod)

        // cache put
        for (i in operation.executions.indices) {
            val cache = operation.executions[i]
            val keyField = "_key${i + 1}"

            if (cache.cacheKey!!.type.type!!.resolve().isMarkedNullable) {
                builder.add("%L?.let { %L.put(it, _value) }\n", keyField, cache.field)
            } else {
                builder.add("%L.put(%L, _value)\n", cache.field, keyField)
            }
        }
        builder.add("return _value")

        return CodeBlock.builder()
            .add(builder.build())
            .build()
    }
}
