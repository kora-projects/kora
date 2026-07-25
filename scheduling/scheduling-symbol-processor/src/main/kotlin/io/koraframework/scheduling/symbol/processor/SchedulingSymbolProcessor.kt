package io.koraframework.scheduling.symbol.processor

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.FunctionKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import io.koraframework.ksp.common.BaseSymbolProcessor
import io.koraframework.ksp.common.FunctionUtils.isSuspend
import io.koraframework.ksp.common.KspCommonUtils.generated
import io.koraframework.ksp.common.exception.ProcessingErrorException
import kotlin.collections.iterator

class SchedulingSymbolProcessor(val env: SymbolProcessorEnvironment) : BaseSymbolProcessor(env) {
    private val triggerTypes: Map<SchedulerType, List<ClassName>> = mapOf(
        SchedulerType.JDK to listOf(
            JdkSchedulingGenerator.scheduleOnce,
            JdkSchedulingGenerator.scheduleWithCron,
            JdkSchedulingGenerator.scheduleAtFixedRate,
            JdkSchedulingGenerator.scheduleWithFixedDelay,
        ),
        SchedulerType.QUARTZ to listOf(
            QuartzSchedulingGenerator.scheduleWithCron,
            QuartzSchedulingGenerator.scheduleWithTrigger,
        ),
        SchedulerType.DB to listOf(
            DbSchedulingGenerator.scheduleOnce,
            DbSchedulingGenerator.scheduleWithCron,
            DbSchedulingGenerator.scheduleWithFixedDelay,
        )
    )
    private val jdkGenerator: JdkSchedulingGenerator = JdkSchedulingGenerator(env)
    private val quartzGenerator: QuartzSchedulingGenerator = QuartzSchedulingGenerator(env)
    private val dbGenerator: DbSchedulingGenerator = DbSchedulingGenerator(env)

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        val scheduledFunctions = triggerTypes.asSequence()
            .flatMap {
                it.value.flatMap { annotationName ->
                    resolver.getSymbolsWithAnnotation(annotationName.canonicalName).map { func ->
                        if (func !is KSFunctionDeclaration) {
                            throw ProcessingErrorException(invalidSchedulingTargetError(annotationName, func::class.simpleName ?: "unknown"), func)
                        }
                        if (func.functionKind != FunctionKind.MEMBER) {
                            throw ProcessingErrorException(invalidSchedulingFunctionError(annotationName, func), func)
                        }
                        if (func.isSuspend()) {
                            throw ProcessingErrorException(suspendSchedulingFunctionError(annotationName, func), func)
                        }
                        func.parentDeclaration!! as KSClassDeclaration to func
                    }
                }
            }
            .groupBy({ it.first }, { it.second })
        for (scheduledFunction in scheduledFunctions) {
            this.generateModule(scheduledFunction.key, scheduledFunction.value)
        }
        return emptyList()
    }

    private fun generateModule(type: KSClassDeclaration, functions: List<KSFunctionDeclaration>) {
        val typeName = type.simpleName.asString()
        val packageName = type.packageName.asString()
        val builder = TypeSpec.interfaceBuilder("\$${typeName}_SchedulingModule")
            .generated(SchedulingSymbolProcessor::class)
            .addAnnotation(ClassName("io.koraframework.common.annotation", "Module"))

        for (function in functions) {
            val trigger = this.parseSchedulerType(function)
            when (trigger.schedulerType) {
                SchedulerType.JDK -> this.jdkGenerator.generate(type, function, builder, trigger)
                SchedulerType.QUARTZ -> this.quartzGenerator.generate(type, function, builder, trigger)
                SchedulerType.DB -> this.dbGenerator.generate(type, function, builder, trigger)
            }
        }
        val module = builder.build()
        FileSpec.get(packageName, module).writeTo(env.codeGenerator, false, listOf(type.containingFile!!))
    }

    private fun parseSchedulerType(function: KSFunctionDeclaration): SchedulingTrigger {
        for (triggerType in this.triggerTypes) {
            for (annotationType in triggerType.value) {
                val shortName = annotationType.simpleName
                val annotation = function.annotations.find {
                    it.shortName.getShortName() == shortName && it.annotationType.resolve().toClassName() == annotationType
                }
                if (annotation != null) {
                    return SchedulingTrigger(triggerType.key, annotation)
                }
            }
        }
        throw IllegalStateException(internalMissingTriggerError(function))
    }

    private fun invalidSchedulingTargetError(annotationName: String, actualKind: String): String {
        val shortName = annotationName.substringAfterLast('.')
        return """
            Invalid scheduling annotation target: `@$shortName`.

            Scheduling annotations can be applied only to functions.
            Actual annotated symbol kind: `$actualKind`.

            Fix: move `@$shortName` to a member function with no arguments.
        """.trimIndent()
    }

    private fun invalidSchedulingFunctionError(annotationName: String, function: KSFunctionDeclaration): String {
        val shortName = annotationName.substringAfterLast('.')
        return """
            Invalid scheduled function: `${function.qualifiedName?.asString()}`.

            `@$shortName` can be applied only to member functions.
            Top-level or local functions cannot be scheduled because Kora needs a component instance to call.

            Fix: move the function into a component class and annotate that member function.
        """.trimIndent()
    }

    private fun suspendSchedulingFunctionError(annotationName: String, function: KSFunctionDeclaration): String {
        val shortName = annotationName.substringAfterLast('.')
        return """
            Invalid scheduled function: `${function.qualifiedName?.asString()}`.

            Suspend methods are not supported by the scheduling generator.
            `@$shortName` can be applied only to regular member functions.

            For structured concurrency, enable Java preview features with --enable-preview and use StructuredTaskScope, for example:

              fun refreshCaches() =
                  StructuredTaskScope.open(
                      StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow<Any>(),
                  ).use { scope ->
                      scope.fork(Callable { userCache.refresh() })
                      scope.fork(Callable { productCache.refresh() })

                      scope.join()
                  }

            Fix: remove suspend from the function.
        """.trimIndent()
    }

    private fun internalMissingTriggerError(function: KSFunctionDeclaration): String {
        return """
            Kora internal error: scheduled function `${function.qualifiedName?.asString()}` has no recognized scheduling trigger annotation.

            The function was collected as scheduled, but trigger parsing could not find the annotation.
            Please report this with the scheduled function source.
        """.trimIndent()
    }
}
