package ru.tinkoff.kora.scheduling.ksp

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValue
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import ru.tinkoff.kora.ksp.common.getOuterClassesAsPrefix
import java.time.Duration

class JdkSchedulingGenerator(val environment: SymbolProcessorEnvironment) {
    private val fixedDelayJobClassName = ClassName("ru.tinkoff.kora.scheduling.jdk", "FixedDelayJob")
    private val fixedRateJobClassName = ClassName("ru.tinkoff.kora.scheduling.jdk", "FixedRateJob")
    private val runOnceJobClassName = ClassName("ru.tinkoff.kora.scheduling.jdk", "RunOnceJob")
    private val jdkSchedulingExecutor = ClassName("ru.tinkoff.kora.scheduling.jdk", "JdkSchedulingExecutor")
    private val schedulingTelemetryFactoryClassName = ClassName("ru.tinkoff.kora.scheduling.common.telemetry", "SchedulingTelemetryFactory")

    fun generate(type: KSClassDeclaration, function: KSFunctionDeclaration, builder: TypeSpec.Builder, trigger: SchedulingTrigger) {
        when (trigger.annotation.shortName.asString()) {
            "ScheduleAtFixedRate" -> this.generateScheduleAtFixedRate(type, function, builder, trigger)
            "ScheduleWithFixedDelay" -> this.generateScheduleWithFixedDelay(type, function, builder, trigger)
            "ScheduleOnce" -> this.generateScheduleOnce(type, function, builder, trigger)
        }
    }


    private fun generateScheduleAtFixedRate(type: KSClassDeclaration, function: KSFunctionDeclaration, builder: TypeSpec.Builder, trigger: SchedulingTrigger) {
        val packageName = type.packageName.asString()
        val configName = trigger.annotation.findValue<String>("config")
        val typeClassName = type.toClassName()
        val jobFunName = type.getOuterClassesAsPrefix() + type.simpleName.getShortName() + "_" + function.simpleName.getShortName() + "_Job";
        val initialDelay = trigger.annotation.findValue<Long>("initialDelay") ?: 0
        val period = trigger.annotation.findValue<Long>("period")
        val unit = trigger.annotation.findValue<KSType>("unit")!!.toClassName()
        val componentFunction = FunSpec.builder(jobFunName)
            .addParameter("telemetryFactory", schedulingTelemetryFactoryClassName)
            .addParameter("service", jdkSchedulingExecutor)
            .addParameter("target", typeClassName)
            .returns(fixedRateJobClassName)
            .addAnnotation(CommonClassNames.root)

        if (configName.isNullOrBlank()) {
            if (period == null || period == 0L) {
                throw ProcessingErrorException("Either period() or config() annotation parameter must be provided", function)
            }
            componentFunction
                .addCode("val initialDelay = %T.of(%L, %L);\n", Duration::class, period, unit)
                .addCode("val period = %T.of(%L, %L);\n", Duration::class, period, unit)
                .addCode("val telemetry = telemetryFactory.get(null, %T::class.java, %S);\n", typeClassName, function.simpleName.getShortName())
        } else {
            val configType = configType(
                type, function,
                ConfigParameter("period", Duration::class.asClassName(), period?.let { CodeBlock.of("%T.of(%L, %L)", Duration::class, it, unit) }),
                ConfigParameter("initialDelay", Duration::class.asClassName(), CodeBlock.of("%T.of(%L, %L)", Duration::class, initialDelay, unit)),
            )
            FileSpec.get(packageName, configType).writeTo(environment.codeGenerator, false, listOf(type.containingFile!!))

            componentFunction
                .addParameter("config", ClassName(packageName, configType.name!!))
                .addCode("val telemetry = telemetryFactory.get(config.telemetry(), %T::class.java, %S);\n", typeClassName, function.simpleName.getShortName())
                .addCode("val period = config.period();\n")
                .addCode("val initialDelay = config.initialDelay();\n")
            builder.addFunction(configComponent(packageName, configType.name!!, configName))
        }
        componentFunction
            .addCode("return %T(telemetry, service, { target.%L() }, initialDelay, period);\n", fixedRateJobClassName, function.simpleName.getShortName())
        builder.addFunction(componentFunction.build())
    }

    private fun generateScheduleWithFixedDelay(type: KSClassDeclaration, function: KSFunctionDeclaration, builder: TypeSpec.Builder, trigger: SchedulingTrigger) {
        val packageName = type.packageName.asString()
        val configName = trigger.annotation.findValue<String>("config")
        val typeClassName = type.toClassName()
        val jobFunName = type.getOuterClassesAsPrefix() + type.simpleName.getShortName() + "_" + function.simpleName.getShortName() + "_Job";
        val initialDelay = trigger.annotation.findValue<Long>("initialDelay") ?: 0
        val delay = trigger.annotation.findValue<Long>("delay")
        val unit = trigger.annotation.findValue<KSType>("unit")!!.toClassName()
        val componentFunction = FunSpec.builder(jobFunName)
            .addParameter("telemetryFactory", schedulingTelemetryFactoryClassName)
            .addParameter("service", jdkSchedulingExecutor)
            .addParameter("target", typeClassName)
            .returns(fixedDelayJobClassName)
            .addAnnotation(CommonClassNames.root)

        if (configName.isNullOrBlank()) {
            if (delay == null || delay == 0L) {
                throw ProcessingErrorException("Either delay() or config() annotation parameter must be provided", function)
            }
            componentFunction
                .addCode("val telemetry = telemetryFactory.get(null, %T::class.java, %S);\n", typeClassName, function.simpleName.getShortName())
                .addCode("val initialDelay = %T.of(%L, %L);\n", Duration::class, delay, unit)
                .addCode("val delay = %T.of(%L, %L);\n", Duration::class, delay, unit)
        } else {
            val configType = configType(
                type, function,
                ConfigParameter("delay", Duration::class.asClassName(), delay?.let { CodeBlock.of("%T.of(%L, %L)", Duration::class, it, unit) }),
                ConfigParameter("initialDelay", Duration::class.asClassName(), CodeBlock.of("%T.of(%L, %L)", Duration::class, initialDelay, unit)),
            )
            FileSpec.get(packageName, configType).writeTo(environment.codeGenerator, false, listOf(type.containingFile!!))

            componentFunction
                .addParameter("config", ClassName(packageName, configType.name!!))
                .addCode("val telemetry = telemetryFactory.get(config.telemetry(), %T::class.java, %S);\n", typeClassName, function.simpleName.getShortName())
                .addCode("val delay = config.delay();\n")
                .addCode("val initialDelay = config.initialDelay();\n")
            builder.addFunction(configComponent(packageName, configType.name!!, configName))
        }
        componentFunction
            .addCode("return %T(telemetry, service, { target.%L() }, initialDelay, delay);\n", fixedDelayJobClassName, function.simpleName.getShortName())
        builder.addFunction(componentFunction.build())
    }

    private fun generateScheduleOnce(type: KSClassDeclaration, function: KSFunctionDeclaration, builder: TypeSpec.Builder, trigger: SchedulingTrigger) {
        val packageName = type.packageName.asString()
        val configName = trigger.annotation.findValue<String>("config")
        val typeClassName = type.toClassName()
        val jobFunName = type.getOuterClassesAsPrefix() + type.simpleName.getShortName() + "_" + function.simpleName.getShortName() + "_Job";
        val delay = trigger.annotation.findValue<Long>("delay")
        val unit = trigger.annotation.findValue<KSType>("unit")!!.toClassName()
        val componentFunction = FunSpec.builder(jobFunName)
            .addParameter("telemetryFactory", schedulingTelemetryFactoryClassName)
            .addParameter("service", jdkSchedulingExecutor)
            .addParameter("target", typeClassName)
            .returns(runOnceJobClassName)
            .addAnnotation(CommonClassNames.root)

        if (configName.isNullOrBlank()) {
            if (delay == null || delay == 0L) {
                throw ProcessingErrorException("Either delay() or config() annotation parameter must be provided", function)
            }
            componentFunction
                .addCode("val telemetry = telemetryFactory.get(null, %T::class.java, %S);\n", typeClassName, function.simpleName.getShortName())
                .addCode("val delay = %T.of(%L, %L);\n", Duration::class, delay, unit)
        } else {
            val configType = configType(
                type, function,
                ConfigParameter("delay", Duration::class.asClassName(), delay?.let { CodeBlock.of("%T.of(%L, %L)", Duration::class, it, unit) })
            )
            FileSpec.get(packageName, configType).writeTo(environment.codeGenerator, false, listOf(type.containingFile!!))

            componentFunction
                .addParameter("config", ClassName(packageName, configType.name!!))
                .addCode("val telemetry = telemetryFactory.get(config.telemetry(), %T::class.java, %S);\n", typeClassName, function.simpleName.getShortName())
                .addCode("val delay = config.delay();\n")
            builder.addFunction(configComponent(packageName, configType.name!!, configName))
        }
        componentFunction
            .addCode("return %T(telemetry, service, { target.%L() }, delay);\n", runOnceJobClassName, function.simpleName.getShortName());
        builder.addFunction(componentFunction.build());
    }

    private fun configComponent(packageName: String, configClassName: String, configPath: String) = FunSpec.builder(configClassName)
        .addParameter("config", CommonClassNames.config)
        .addParameter(
            "extractor", CommonClassNames.configValueExtractor
            .parameterizedBy(ClassName(packageName, configClassName))
        )
        .addCode("val configValue = config.get(%S);\n", configPath)
        .addStatement("return extractor.extract(configValue) ?: throw %T.missingValueAfterParse(configValue)", CommonClassNames.configValueExtractionException)
        .returns(ClassName(packageName, configClassName))
        .build()

    private data class ConfigParameter(val name: String, val type: ClassName, val defaultValue: CodeBlock?)

    private fun configType(type: KSClassDeclaration, function: KSFunctionDeclaration, vararg params: ConfigParameter): TypeSpec {
        val configClassName = type.getOuterClassesAsPrefix() + type.simpleName.getShortName() + "_" + function.simpleName.getShortName() + "_Config"
        val configType = TypeSpec.interfaceBuilder(configClassName)
            .addAnnotation(CommonClassNames.configValueExtractorAnnotation)
            .generated(JdkSchedulingGenerator::class)
            .addFunction(FunSpec.builder("telemetry").returns(ClassName("ru.tinkoff.kora.telemetry.common", "TelemetryConfig")).addModifiers(KModifier.ABSTRACT).build())
        for (param in params) {
            configType.addFunction(
                FunSpec.builder(param.name)
                    .returns(param.type)
                    .apply {
                        param.defaultValue?.let {
                            addStatement("return %L", it)
                        }
                    }
                    .build()
            )
        }
        return configType.build()
    }
}

