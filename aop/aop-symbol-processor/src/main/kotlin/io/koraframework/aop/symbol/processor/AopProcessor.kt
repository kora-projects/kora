package io.koraframework.aop.symbol.processor

import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.isProtected
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.toTypeVariableName
import io.koraframework.ksp.common.AnnotationUtils.isAnnotationPresent
import io.koraframework.ksp.common.CommonClassNames
import io.koraframework.ksp.common.FunctionUtils.isDeferred
import io.koraframework.ksp.common.FunctionUtils.isFlow
import io.koraframework.ksp.common.FunctionUtils.isSuspend
import io.koraframework.ksp.common.KoraSymbolProcessingEnv
import io.koraframework.ksp.common.KspCommonUtils.addOriginatingKSFile
import io.koraframework.ksp.common.KspCommonUtils.generated
import io.koraframework.ksp.common.KspCommonUtils.resolveToUnderlying
import io.koraframework.ksp.common.TagUtils.parseTag
import io.koraframework.ksp.common.TagUtils.toTagAnnotation
import io.koraframework.ksp.common.exception.ProcessingErrorException
import io.koraframework.ksp.common.findMethods
import kotlin.reflect.KClass

class AopProcessor(private val aspects: List<KoraAspect>, private val resolver: Resolver) {

    private class TypeFieldFactory(private val resolver: Resolver) : KoraAspect.FieldFactory {
        private val fieldNames: MutableSet<String> = HashSet()
        private val constructorParams: MutableMap<ConstructorParamKey, String> = linkedMapOf()
        private val constructorInitializedParams: MutableMap<ConstructorInitializedParamKey, String> = linkedMapOf()

        private data class ConstructorParamKey(val type: TypeName, val annotations: List<AnnotationSpec>, val resolver: Resolver)
        private data class ConstructorInitializedParamKey(val type: TypeName, val initBlock: CodeBlock, val resolver: Resolver)

        override fun constructorParam(type: TypeName, annotations: List<AnnotationSpec>): String {
            return constructorParams.computeIfAbsent(ConstructorParamKey(type, annotations, resolver)) { key ->
                this.computeFieldName(key.type)
            }
        }

        override fun constructorInitialized(type: TypeName, initializer: CodeBlock): String {
            return constructorInitializedParams.computeIfAbsent(ConstructorInitializedParamKey(type, initializer, resolver)) { key ->
                this.computeFieldName(key.type)
            }
        }

        fun addFields(typeBuilder: TypeSpec.Builder) {
            constructorParams.forEach { (fd, name) ->
                typeBuilder.addProperty(
                    PropertySpec.builder(name, fd.type, KModifier.PRIVATE, KModifier.FINAL)
                        .initializer(name)
                        .build()
                )
            }
            constructorInitializedParams.forEach { (fd, name) ->
                typeBuilder.addProperty(
                    PropertySpec.builder(name, fd.type, KModifier.PRIVATE, KModifier.FINAL).build()
                )
            }
        }

        fun enrichConstructor(constructorBuilder: FunSpec.Builder) {
            constructorParams.forEach { (fd, name) ->
                constructorBuilder.addParameter(
                    ParameterSpec.builder(name, fd.type)
                        .addAnnotations(fd.annotations)
                        .build()
                )
            }
            constructorInitializedParams.forEach { (fd, name) ->
                constructorBuilder.addCode("this.%L = %L\n", name, fd.initBlock)
            }
        }

        private fun computeFieldName(type: TypeName): String {
            val qualifiedType = if (type is ParameterizedTypeName) type.rawType else type as ClassName
            val shortName = qualifiedType.simpleName.replaceFirstChar { it.lowercaseChar() }
            for (i in 1 until Int.MAX_VALUE) {
                val name = shortName + i
                if (fieldNames.add(name)) {
                    return name
                }
            }
            // never gonna happen
            throw IllegalStateException(
                """
                Kora internal error: failed to allocate a unique AOP proxy field name for type '$qualifiedType'.

                This should be unreachable because field names are generated with an increasing numeric suffix.
                """.trimIndent()
            )
        }
    }

    fun applyAspects(classDeclaration: KSClassDeclaration): TypeSpec {
        val constructor = classDeclaration.findAopConstructor()
            ?: throw ProcessingErrorException(aopConstructorError(classDeclaration), classDeclaration)

        val typeLevelAspects: ArrayList<KoraAspect> = ArrayList()

        for (am in classDeclaration.annotations) {
            val annotationType = am.annotationType.resolveToUnderlying().declaration.qualifiedName?.asString()
                ?: continue

            for (aspect in aspects) {
                val supportedAnnotationTypes = aspect.getSupportedAnnotationTypes()
                if (supportedAnnotationTypes.contains(annotationType)) {
                    if (!typeLevelAspects.contains(aspect)) {
                        typeLevelAspects.add(aspect)
                    }
                }
            }
        }
        KoraSymbolProcessingEnv.logger.logging("Type level aspects for ${classDeclaration.qualifiedName!!.asString()}}: {$typeLevelAspects}", classDeclaration)
        val typeBuilder: TypeSpec.Builder = TypeSpec.classBuilder(classDeclaration.aopProxyName())
            .addOriginatingKSFile(classDeclaration)
            .superclass(classDeclaration.toClassName())
            .addModifiers(KModifier.PUBLIC, KModifier.FINAL)
            .addAnnotation(CommonClassNames.aopProxy)

        val typeFieldFactory = TypeFieldFactory(resolver)
        val aopContext: KoraAspect.AspectContext = KoraAspect.AspectContext(typeBuilder, typeFieldFactory)

        classDeclaration.parseTag().let { tags ->
            if (tags != null) {
                typeBuilder.addAnnotation(tags.toTagAnnotation())
            }
        }
        if (classDeclaration.isAnnotationPresent(CommonClassNames.root)) {
            typeBuilder.addAnnotation(CommonClassNames.root)
        }

        val classFunctions = findMethods(classDeclaration) { f ->
            !f.isConstructor() && (f.isPublic() || f.isProtected())
        }

        val methodAspectsApplied = linkedSetOf<KoraAspect>()
        classFunctions.forEach { function ->
            val methodLevelTypeAspects = typeLevelAspects.toMutableList()
            val methodLevelAspects = mutableListOf<KoraAspect>()
            val methodParameterLevelAspects = mutableListOf<KoraAspect>()
            val functionAnnotations = function.annotations.toList()
            for (am in functionAnnotations) {
                val annotationType = am.annotationType.resolveToUnderlying().declaration.qualifiedName?.asString()
                    ?: continue
                aspects.forEach { aspect ->
                    val supportedAnnotationTypes = aspect.getSupportedAnnotationTypes()
                    if (supportedAnnotationTypes.contains(annotationType)) {
                        if (!methodLevelAspects.contains(aspect)) {
                            methodLevelAspects.add(aspect)
                        }
                        methodLevelTypeAspects.remove(aspect)
                    }
                }
            }
            function.parameters.forEach { parameter ->
                for (am in parameter.annotations) {
                    val annotationType = am.annotationType.resolveToUnderlying().declaration.qualifiedName?.asString()
                        ?: continue

                    aspects.forEach { aspect ->
                        val supportedAnnotationTypes = aspect.getSupportedAnnotationTypes()
                        if (supportedAnnotationTypes.contains(annotationType)) {
                            if (!methodParameterLevelAspects.contains(aspect) && !methodLevelAspects.contains(aspect)) {
                                methodParameterLevelAspects.add(aspect)
                            }
                            methodLevelTypeAspects.remove(aspect)
                        }
                    }
                }
            }
            if (methodLevelTypeAspects.isEmpty() && methodLevelAspects.isEmpty() && methodParameterLevelAspects.isEmpty()) {
                return@forEach
            }
            KoraSymbolProcessingEnv.logger.logging(
                "Method level aspects for ${classDeclaration.qualifiedName!!.asString()}}#${function.simpleName.asString()}: {$methodLevelAspects}",
                classDeclaration
            )
            val aspectsToApply = methodLevelTypeAspects.toMutableList()
            aspectsToApply.addAll(methodLevelAspects)
            aspectsToApply.addAll(methodParameterLevelAspects)
            coroutineAspectMethodError(classDeclaration, function, aspectsToApply)?.let {
                throw ProcessingErrorException(it, function)
            }

            var superCall = "super." + function.simpleName.asString()
            val overridenMethod = FunSpec.builder(function.simpleName.asString())
                .addModifiers(KModifier.OVERRIDE)
            function.returnType?.resolve()?.let { overridenMethod.returns(it.toTypeName()) }

            if (function.modifiers.contains(Modifier.SUSPEND)) {
                overridenMethod.addModifiers(KModifier.SUSPEND)
            }

            aspectsToApply.reverse()
            val generatedMethodNames = mutableSetOf<String>()
            var isMethodAspectApplied = false
            for (aspect in aspectsToApply) {
                val result: KoraAspect.ApplyResult = aspect.apply(function, superCall, aopContext)
                if (result is KoraAspect.ApplyResult.Noop) {
                    continue
                }

                val methodBody: KoraAspect.ApplyResult.MethodBody = result as KoraAspect.ApplyResult.MethodBody
                val baseMethodName = "_" + function.simpleName.asString() + "_AopProxy_" + aspect::class.simpleName
                var methodName = baseMethodName
                if (!generatedMethodNames.add(methodName)) {
                    for (i in 0..Int.MAX_VALUE) {
                        methodName = baseMethodName + i
                        if (generatedMethodNames.add(methodName)) {
                            break
                        }
                    }
                }

                superCall = methodName
                val f = FunSpec.builder(methodName)
                    .addModifiers(KModifier.PRIVATE)
                    .addCode(methodBody.codeBlock)

                if (function.modifiers.contains(Modifier.SUSPEND)) {
                    f.addModifiers(KModifier.SUSPEND)
                }

                function.parameters.forEach { parameter ->
                    val paramSpec = ParameterSpec.builder(parameter.name!!.asString(), parameter.type.resolve().toTypeName()).build()
                    if (!overridenMethod.parameters.contains(paramSpec)) {
                        overridenMethod.addParameter(paramSpec)
                    }
                    f.addParameter(paramSpec)
                }
                function.typeParameters.forEach { typeParameter ->
                    overridenMethod.addTypeVariable(typeParameter.toTypeVariableName())
                    f.addTypeVariable(typeParameter.toTypeVariableName())
                }
                val returnType = function.returnType!!.resolve()
                f.returns(returnType.toTypeName())
                typeBuilder.addFunction(f.build())
                methodAspectsApplied.add(aspect)
                isMethodAspectApplied = true
            }

            if (isMethodAspectApplied) {
                val b = CodeBlock.builder()
                if (function.returnType!!.resolve() != resolver.builtIns.unitType) {
                    b.add("return ")
                }
                b.add("%L(", superCall)
                for (i in function.parameters.indices) {
                    if (i > 0) {
                        b.add(", ")
                    }
                    val parameter = function.parameters[i]
                    b.add("%L", parameter)
                }
                b.add(")\n")
                overridenMethod.addCode(b.build())
                typeBuilder.addFunction(overridenMethod.build())
            }
        }

        val generatedClasses = mutableListOf<KClass<*>>()
        generatedClasses.add(AopSymbolProcessor::class)
        methodAspectsApplied.forEach { generatedClasses.add(it::class) }

        typeBuilder.generated(generatedClasses)

        if (classDeclaration.isAnnotationPresent(CommonClassNames.component)) {
            typeBuilder.addAnnotation(CommonClassNames.component)
        }

        val constructorBuilder = FunSpec.constructorBuilder()
        for (i in constructor.parameters.indices) {
            val parameter = constructor.parameters[i]
            typeBuilder.addSuperclassConstructorParameter("%L", parameter.name!!.asString())
            val parameterSpec = ParameterSpec.builder(parameter.name!!.asString(), parameter.type.resolve().toTypeName())

            parameter.parseTag()?.let { tags ->
                parameterSpec.addAnnotation(tags.toTagAnnotation())
            }

            constructorBuilder.addParameter(parameterSpec.build())
        }
        typeFieldFactory.addFields(typeBuilder)
        typeFieldFactory.enrichConstructor(constructorBuilder)
        typeBuilder.primaryConstructor(constructorBuilder.build())
        return typeBuilder.build()
    }

    private fun coroutineAspectMethodError(classDeclaration: KSClassDeclaration, function: KSFunctionDeclaration, aspects: List<KoraAspect>): String? {
        val (problem, reason, fix) = when {
            function.isSuspend() -> Triple(
                "Suspend methods are not supported by Kora aspects",
                """
                A suspend function can resume on another thread, so ScopedValue-backed context (tracing, MDC, JDBC transaction)
                set by the aspect is lost, and calling it from blocking code requires runBlocking, which blocks the worker thread.
                """.trimIndent(),
                "Remove suspend from the method"
            )

            function.isFlow() -> Triple(
                "Methods returning kotlinx.coroutines.flow.Flow are not supported by Kora aspects",
                """
                Flow is cold: the method only builds the flow and the work runs later, when a coroutine collects it.
                An aspect wraps the method call, so it would observe only the flow creation, and the collection runs
                outside the ScopedValue-backed context (tracing, MDC, JDBC transaction) set by the aspect.
                """.trimIndent(),
                "Return the collected result (for example List<T>) from a regular method"
            )

            function.isDeferred() -> Triple(
                "Methods returning kotlinx.coroutines.Deferred are not supported by Kora aspects",
                """
                Deferred is a running coroutine: the aspect would observe only its start, and the coroutine completes later
                on another thread, outside the ScopedValue-backed context (tracing, MDC, JDBC transaction) set by the aspect.
                """.trimIndent(),
                "Return the result from a regular method"
            )

            else -> return null
        }
        val supported = aspects.flatMap { it.getSupportedAnnotationTypes() }.toSet()
        val annotations = (function.annotations + function.parameters.asSequence().flatMap { it.annotations } + classDeclaration.annotations)
            .mapNotNull { it.annotationType.resolveToUnderlying().declaration.qualifiedName?.asString() }
            .filter { it in supported }
            .distinct()
            .joinToString(", ") { "@" + it.substringAfterLast('.') }
        return """
            |AOP proxy method is invalid:
            |  ${classDeclaration.qualifiedName?.asString()}#${function.simpleName.asString()}
            |
            |Problem:
            |  $problem ($annotations).
            |
            |Hint:
            |${reason.prependIndent("  ")}
            |  Aspects support synchronous methods running on virtual threads. For structured concurrency, enable Java
            |  preview features with --enable-preview and use StructuredTaskScope, for example:
            |
            |    fun getQuote(id: Long): Quote =
            |        StructuredTaskScope.open(
            |            StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow<Any>(),
            |        ).use { scope ->
            |            val price = scope.fork(Callable { priceService.getPrice(id) })
            |            val stock = scope.fork(Callable { stockService.getStock(id) })
            |
            |            scope.join()
            |
            |            Quote(price.get(), stock.get())
            |        }
            |
            |Fix:
            |  $fix, or remove $annotations from it.
        """.trimMargin()
    }

    private fun aopConstructorError(classDeclaration: KSClassDeclaration): String {
        return """
            AOP proxy cannot be generated for '${classDeclaration.qualifiedName?.asString()}': no suitable constructor was found.

            Fix: provide at least one public or protected constructor that can be called from the generated proxy. Private constructors and constructors with unsupported visibility cannot be used for AOP proxy generation.
        """.trimIndent()
    }
}
