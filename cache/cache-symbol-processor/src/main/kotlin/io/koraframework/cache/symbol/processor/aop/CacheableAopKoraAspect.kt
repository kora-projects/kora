package io.koraframework.cache.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.CodeBlock
import io.koraframework.aop.symbol.processor.KoraAspect
import io.koraframework.cache.symbol.processor.CacheOperation
import io.koraframework.cache.symbol.processor.CacheOperationUtils
import io.koraframework.cache.symbol.processor.CacheOperationUtils.Companion.ANNOTATION_CACHEABLE
import io.koraframework.cache.symbol.processor.CacheOperationUtils.Companion.ANNOTATION_CACHEABLES
import io.koraframework.ksp.common.CommonClassNames
import io.koraframework.ksp.common.FunctionUtils.isCompletionStage
import io.koraframework.ksp.common.FunctionUtils.isFuture
import io.koraframework.ksp.common.FunctionUtils.isPublisher
import io.koraframework.ksp.common.FunctionUtils.isVoid
import io.koraframework.ksp.common.exception.ProcessingErrorException
import java.util.concurrent.CompletionStage
import java.util.concurrent.Future

@KspExperimental
class CacheableAopKoraAspect(private val resolver: Resolver) : AbstractAopCacheAspect() {

    override fun getSupportedAnnotationTypes(): Set<String> {
        return setOf(ANNOTATION_CACHEABLE.canonicalName, ANNOTATION_CACHEABLES.canonicalName)
    }

    override fun apply(ksFunction: KSFunctionDeclaration, superCall: String, aspectContext: KoraAspect.AspectContext): KoraAspect.ApplyResult {
        if (ksFunction.isFuture()) {
            throw ProcessingErrorException("@${ANNOTATION_CACHEABLE.simpleName} can't be applied for types assignable from ${Future::class.java}", ksFunction)
        } else if (ksFunction.isCompletionStage()) {
            throw ProcessingErrorException("@${ANNOTATION_CACHEABLE.simpleName} can't be applied for types assignable from ${CompletionStage::class.java}", ksFunction)
        } else if (ksFunction.isPublisher()) {
            throw ProcessingErrorException("@${ANNOTATION_CACHEABLE.simpleName} can't be applied for types assignable from ${CommonClassNames.publisher}", ksFunction)
        } else if (ksFunction.isVoid()) {
            throw ProcessingErrorException("@${ANNOTATION_CACHEABLE.simpleName} can't be applied for types assignable from ${Void::class.java}", ksFunction)
        }

        val operation = CacheOperationUtils.Companion.getCacheOperation(ksFunction, aspectContext)
        val body = buildBodySync(ksFunction, operation, superCall, aspectContext)
        return KoraAspect.ApplyResult.MethodBody(body)
    }

    private fun buildBodySync(
        method: KSFunctionDeclaration,
        operation: CacheOperation,
        superCall: String,
        aspectContext: KoraAspect.AspectContext,
    ): CodeBlock {
        val superMethod = getSuperMethod(method, superCall)
        val executorField = getExecutorField(operation, aspectContext)
        val builder = CodeBlock.builder()

        val isResultNullable = method.returnType!!.resolve().isMarkedNullable
        val suffixCheck = if (isResultNullable) "" else "!!"
        if (operation.executions.size == 1) {
            val cache = operation.executions[0]
            val keyBlock = CodeBlock.of("val _key = %L\n", cache.cacheKey!!.code)
            if (isAsync(cache)) {
                return buildSingleBodyAsync(cache, superMethod, keyBlock, suffixCheck, executorField)
            }

            val isSingleNullableParam = cache.type.isMarkedNullable
            val codeBlock = if (isSingleNullableParam) {
                CodeBlock.of(
                    """
                        return if (_key != null) {
                            %L.computeIfAbsent(_key) { %L }%L
                        } else {
                            %L
                        }
                    """.trimIndent(), cache.field, superMethod, suffixCheck, superMethod
                )
            } else {
                CodeBlock.of("return %L.computeIfAbsent(_key) { %L }%L", cache.field, superMethod, suffixCheck)
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
                val prevCacheKeyField = "_key${j + 1}"
                if (isAsync(prevCache)) {
                    builder.add("\tval _asyncValue%L = _value\n", j)
                    builder.add("\t").add(cachePut(executorField, prevCache, prevCacheKeyField, "_asyncValue$j"))
                } else {
                    builder.add("\t%L.put(%L, _value)\n", prevCache.field, prevCacheKeyField)
                }
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
                builder.add("%L?.let { ", keyField)
                builder.add(cachePut(executorField, cache, "it", "_value"))
                builder.add("}\n")
            } else {
                builder.add(cachePut(executorField, cache, keyField, "_value"))
            }
        }
        builder.add("return _value")

        return CodeBlock.builder()
            .add(builder.build())
            .build()
    }

    private fun buildSingleBodyAsync(
        cache: CacheOperation.CacheExecution,
        superMethod: String,
        keyBlock: CodeBlock,
        suffixCheck: String,
        executorField: String?,
    ): CodeBlock {
        val builder = CodeBlock.builder()
            .add(keyBlock)

        if (cache.type.isMarkedNullable) {
            builder.add(
                """
                    if (_key == null) {
                        return %L
                    }
                    
                """.trimIndent(),
                superMethod
            )
        }

        builder.add("val _value = %L.get(_key)\n", cache.field)
        builder.add(
            """
                if (_value != null) {
                    return _value%L
                }
                
            """.trimIndent(),
            suffixCheck
        )
        builder.add("val _result = %L\n", superMethod)
        builder.add(cachePut(executorField, cache, "_key", "_result"))
        builder.add("return _result")
        return builder.build()
    }
}
