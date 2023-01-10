package ru.tinkoff.kora.scheduling.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.FunctionKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.ksp.common.BaseSymbolProcessor
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated

class SchedulingKsp(val env: SymbolProcessorEnvironment) : BaseSymbolProcessor(env) {
    private val triggerTypes: Map<SchedulerType, List<String>> = mapOf(
        SchedulerType.JDK to listOf(
            "ru.tinkoff.kora.scheduling.jdk.annotation.ScheduleAtFixedRate",
            "ru.tinkoff.kora.scheduling.jdk.annotation.ScheduleOnce",
            "ru.tinkoff.kora.scheduling.jdk.annotation.ScheduleWithFixedDelay"
        ),
        SchedulerType.QUARTZ to listOf(
            "ru.tinkoff.kora.scheduling.quartz.ScheduleWithTrigger",
            "ru.tinkoff.kora.scheduling.quartz.ScheduleWithCron"
        )
    )
    private val jdkGenerator: JdkSchedulingGenerator = JdkSchedulingGenerator(env)
    private val quartzGenerator: QuartzSchedulingGenerator = QuartzSchedulingGenerator(env)

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        val scheduledFunctions = triggerTypes.asSequence()
            .flatMap {
                it.value.flatMap { annotationName ->
                    resolver.getSymbolsWithAnnotation(annotationName).map { func ->
                        if (func !is KSFunctionDeclaration) {
                            throw IllegalArgumentException("Annotation should be on method")
                        }
                        if (func.functionKind != FunctionKind.MEMBER) {
                            throw IllegalArgumentException("Function should be member function")
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
            .generated(SchedulingKsp::class)
            .addAnnotation(ClassName("ru.tinkoff.kora.common", "Module"))

        for (function in functions) {
            val trigger = this.parseSchedulerType(function)
            when (trigger.schedulerType) {
                SchedulerType.JDK -> this.jdkGenerator.generate(type, function, builder, trigger)
                SchedulerType.QUARTZ -> this.quartzGenerator.generate(type, function, builder, trigger)
            }
        }
        val module = builder.build()
        FileSpec.get(packageName, module).writeTo(env.codeGenerator, false, listOf(type.containingFile!!))
    }

    private fun parseSchedulerType(function: KSFunctionDeclaration): SchedulingTrigger {
        for (triggerType in this.triggerTypes) {
            for (annotationType in triggerType.value) {
                val shortName = annotationType.substringAfterLast('.')
                val annotation = function.annotations.find {
                    it.shortName.getShortName() == shortName && it.annotationType.resolve().declaration.qualifiedName!!.asString() == annotationType
                }
                if (annotation != null) {
                    return SchedulingTrigger(triggerType.key, annotation)
                }
            }
        }
        throw IllegalArgumentException()
    }
}
