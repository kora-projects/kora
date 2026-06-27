package io.koraframework.scheduling.symbol.processor

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import io.koraframework.ksp.common.AnnotationUtils.findEnumValue
import io.koraframework.ksp.common.AnnotationUtils.findValue
import io.koraframework.ksp.common.CommonClassNames
import io.koraframework.ksp.common.KotlinPoetUtils.controlFlow
import io.koraframework.ksp.common.KspCommonUtils.generated
import io.koraframework.ksp.common.exception.ProcessingErrorException
import io.koraframework.ksp.common.getOuterClassesAsPrefix
import java.time.Duration

class JdkSchedulingGenerator(val environment: SymbolProcessorEnvironment) {
    private val fixedDelayJobClassName = ClassName("io.koraframework.scheduling.jdk", "FixedDelayJob")
    private val fixedRateJobClassName = ClassName("io.koraframework.scheduling.jdk", "FixedRateJob")
    private val runOnceJobClassName = ClassName("io.koraframework.scheduling.jdk", "RunOnceJob")
    private val cronJobClassName = ClassName("io.koraframework.scheduling.jdk", "CronJob")
    private val cronExpressionClassName = ClassName("io.koraframework.scheduling.jdk", "CronExpression")
    private val jdkSchedulingExecutor = ClassName("io.koraframework.scheduling.jdk", "SchedulingJdkExecutor")
    private val schedulingTelemetryFactoryClassName = ClassName("io.koraframework.scheduling.common.telemetry", "SchedulingTelemetryFactory")
    private val schedulingJobConfigClassName = ClassName("io.koraframework.scheduling.common", "SchedulingJobConfig")
    private val jobTelemetryConfigClassName = ClassName("io.koraframework.scheduling.common", "SchedulingJobConfig", "JobTelemetryConfig")

    fun generate(type: KSClassDeclaration, function: KSFunctionDeclaration, builder: TypeSpec.Builder, trigger: SchedulingTrigger) {
        when (trigger.annotation.shortName.asString()) {
            "ScheduleAtFixedRate" -> this.generateScheduleAtFixedRate(type, function, builder, trigger)
            "ScheduleWithFixedDelay" -> this.generateScheduleWithFixedDelay(type, function, builder, trigger)
            "ScheduleOnce" -> this.generateScheduleOnce(type, function, builder, trigger)
            "ScheduleWithCron" -> this.generateScheduleWithCron(type, function, builder, trigger)
        }
    }

    private fun generateScheduleWithCron(type: KSClassDeclaration, function: KSFunctionDeclaration, builder: TypeSpec.Builder, trigger: SchedulingTrigger) {
        val packageName = type.packageName.asString()
        val configName = trigger.annotation.findValue<String>("config")
        val typeClassName = type.toClassName()
        val jobFunName = type.getOuterClassesAsPrefix() + type.simpleName.getShortName() + "_" + function.simpleName.getShortName() + "_Job"
        val cron = trigger.annotation.findValue<String>("value")
        val componentFunction = FunSpec.builder(jobFunName)
            .addParameter("telemetryFactory", schedulingTelemetryFactoryClassName)
            .addParameter("service", jdkSchedulingExecutor)
            .addParameter("target", CommonClassNames.valueOf.parameterizedBy(typeClassName))
            .returns(cronJobClassName)
            .addAnnotation(CommonClassNames.root)

        if (configName.isNullOrBlank()) {
            if (cron.isNullOrBlank()) {
                throw ProcessingErrorException("Either value() or config() annotation parameter must be provided", function)
            }
            componentFunction
                .addStatement("val telemetry = telemetryFactory.get(null, null, %T::class.java, %S)", typeClassName, function.simpleName.getShortName())
                .addStatement("val cron = %T.parse(%S)", cronExpressionClassName, cron)
        } else {
            val configType = cronConfigType(type, function, cron ?: "")
            FileSpec.get(packageName, configType).writeTo(environment.codeGenerator, false, listOf(type.containingFile!!))

            componentFunction
                .addParameter("config", ClassName(packageName, configType.name!!))
                .addStatement("val telemetry = telemetryFactory.get(%S, config.telemetry(), %T::class.java, %S)", configName, typeClassName, function.simpleName.getShortName())
                .addStatement("val cron = %T.parse(config.cron())", cronExpressionClassName)
            builder.addFunction(cronConfigComponent(packageName, configType.name!!, configName, cron ?: ""))
        }
        componentFunction
            .addStatement("return %T(telemetry, service, { target.get().%N() }, cron)", cronJobClassName, function.simpleName.getShortName())
        builder.addFunction(componentFunction.build())
    }


    private fun generateScheduleAtFixedRate(type: KSClassDeclaration, function: KSFunctionDeclaration, builder: TypeSpec.Builder, trigger: SchedulingTrigger) {
        val packageName = type.packageName.asString()
        val configName = trigger.annotation.findValue<String>("config")
        val typeClassName = type.toClassName()
        val jobFunName = type.getOuterClassesAsPrefix() + type.simpleName.getShortName() + "_" + function.simpleName.getShortName() + "_Job"
        val initialDelay = trigger.annotation.findValue<Long>("initialDelay") ?: 0
        val period = trigger.annotation.findValue<Long>("period")
        val unit = trigger.annotation.findEnumValue("unit")!!
        val componentFunction = FunSpec.builder(jobFunName)
            .addParameter("telemetryFactory", schedulingTelemetryFactoryClassName)
            .addParameter("service", jdkSchedulingExecutor)
            .addParameter("target", CommonClassNames.valueOf.parameterizedBy(typeClassName))
            .returns(fixedRateJobClassName)
            .addAnnotation(CommonClassNames.root)

        if (configName.isNullOrBlank()) {
            if (period == null || period == 0L) {
                throw ProcessingErrorException("Either period() or config() annotation parameter must be provided", function)
            }
            componentFunction
                .addStatement("val initialDelay = %T.of(%L, %L)", Duration::class, initialDelay, unit)
                .addStatement("val period = %T.of(%L, %L)", Duration::class, period, unit)
                .addStatement("val telemetry = telemetryFactory.get(null, null, %T::class.java, %S)", typeClassName, function.simpleName.getShortName())
        } else {
            val configType = configType(
                type, function,
                ConfigParameter("period", Duration::class.asClassName(), period?.let { CodeBlock.of("%T.of(%L, %L)", Duration::class, it, unit) }),
                ConfigParameter("initialDelay", Duration::class.asClassName(), CodeBlock.of("%T.of(%L, %L)", Duration::class, initialDelay, unit)),
            )
            FileSpec.get(packageName, configType).writeTo(environment.codeGenerator, false, listOf(type.containingFile!!))

            componentFunction
                .addParameter("config", ClassName(packageName, configType.name!!))
                .addStatement("val telemetry = telemetryFactory.get(%S, config.telemetry(), %T::class.java, %S)", configName, typeClassName, function.simpleName.getShortName())
                .addStatement("val period = config.period()")
                .addStatement("val initialDelay = config.initialDelay()")
            builder.addFunction(configComponent(packageName, configType.name!!, configName))
        }
        componentFunction
            .addStatement("return %T(telemetry, service, { target.get().%N() }, initialDelay, period)", fixedRateJobClassName, function.simpleName.getShortName())
        builder.addFunction(componentFunction.build())
    }

    private fun generateScheduleWithFixedDelay(type: KSClassDeclaration, function: KSFunctionDeclaration, builder: TypeSpec.Builder, trigger: SchedulingTrigger) {
        val packageName = type.packageName.asString()
        val configName = trigger.annotation.findValue<String>("config")
        val typeClassName = type.toClassName()
        val jobFunName = type.getOuterClassesAsPrefix() + type.simpleName.getShortName() + "_" + function.simpleName.getShortName() + "_Job"
        val initialDelay = trigger.annotation.findValue<Long>("initialDelay") ?: 0
        val delay = trigger.annotation.findValue<Long>("delay")
        val unit = trigger.annotation.findEnumValue("unit")!!
        val componentFunction = FunSpec.builder(jobFunName)
            .addParameter("telemetryFactory", schedulingTelemetryFactoryClassName)
            .addParameter("service", jdkSchedulingExecutor)
            .addParameter("target", CommonClassNames.valueOf.parameterizedBy(typeClassName))
            .returns(fixedDelayJobClassName)
            .addAnnotation(CommonClassNames.root)

        if (configName.isNullOrBlank()) {
            if (delay == null || delay == 0L) {
                throw ProcessingErrorException("Either delay() or config() annotation parameter must be provided", function)
            }
            componentFunction
                .addStatement("val telemetry = telemetryFactory.get(null, null, %T::class.java, %S)", typeClassName, function.simpleName.getShortName())
                .addStatement("val initialDelay = %T.of(%L, %L)", Duration::class, initialDelay, unit)
                .addStatement("val delay = %T.of(%L, %L)", Duration::class, delay, unit)
        } else {
            val configType = configType(
                type, function,
                ConfigParameter("delay", Duration::class.asClassName(), delay?.let { CodeBlock.of("%T.of(%L, %L)", Duration::class, it, unit) }),
                ConfigParameter("initialDelay", Duration::class.asClassName(), CodeBlock.of("%T.of(%L, %L)", Duration::class, initialDelay, unit)),
            )
            FileSpec.get(packageName, configType).writeTo(environment.codeGenerator, false, listOf(type.containingFile!!))

            componentFunction
                .addParameter("config", ClassName(packageName, configType.name!!))
                .addStatement("val telemetry = telemetryFactory.get(%S, config.telemetry(), %T::class.java, %S)", configName, typeClassName, function.simpleName.getShortName())
                .addStatement("val delay = config.delay()")
                .addStatement("val initialDelay = config.initialDelay()")
            builder.addFunction(configComponent(packageName, configType.name!!, configName))
        }
        componentFunction
            .addStatement("return %T(telemetry, service, { target.get().%N() }, initialDelay, delay)", fixedDelayJobClassName, function.simpleName.getShortName())
        builder.addFunction(componentFunction.build())
    }

    private fun generateScheduleOnce(type: KSClassDeclaration, function: KSFunctionDeclaration, builder: TypeSpec.Builder, trigger: SchedulingTrigger) {
        val packageName = type.packageName.asString()
        val configName = trigger.annotation.findValue<String>("config")
        val typeClassName = type.toClassName()
        val jobFunName = type.getOuterClassesAsPrefix() + type.simpleName.getShortName() + "_" + function.simpleName.getShortName() + "_Job"
        val delay = trigger.annotation.findValue<Long>("delay")
        val unit = trigger.annotation.findEnumValue("unit")!!
        val componentFunction = FunSpec.builder(jobFunName)
            .addParameter("telemetryFactory", schedulingTelemetryFactoryClassName)
            .addParameter("service", jdkSchedulingExecutor)
            .addParameter("target", CommonClassNames.valueOf.parameterizedBy(typeClassName))
            .returns(runOnceJobClassName)
            .addAnnotation(CommonClassNames.root)

        if (configName.isNullOrBlank()) {
            if (delay == null || delay == 0L) {
                throw ProcessingErrorException("Either delay() or config() annotation parameter must be provided", function)
            }
            componentFunction
                .addStatement("val telemetry = telemetryFactory.get(null, null, %T::class.java, %S)", typeClassName, function.simpleName.getShortName())
                .addStatement("val delay = %T.of(%L, %L)", Duration::class, delay, unit)
        } else {
            val configType = configType(
                type, function,
                ConfigParameter("delay", Duration::class.asClassName(), delay?.let { CodeBlock.of("%T.of(%L, %L)", Duration::class, it, unit) })
            )
            FileSpec.get(packageName, configType).writeTo(environment.codeGenerator, false, listOf(type.containingFile!!))

            componentFunction
                .addParameter("config", ClassName(packageName, configType.name!!))
                .addStatement("val telemetry = telemetryFactory.get(%S, config.telemetry(), %T::class.java, %S)", configName, typeClassName, function.simpleName.getShortName())
                .addStatement("val delay = config.delay()")
            builder.addFunction(configComponent(packageName, configType.name!!, configName))
        }
        componentFunction
            .addStatement("return %T(telemetry, service, { target.get().%N() }, delay)", runOnceJobClassName, function.simpleName.getShortName())
        builder.addFunction(componentFunction.build())
    }

    private fun configComponent(packageName: String, configClassName: String, configPath: String) = FunSpec.builder(configClassName)
        .addParameter("config", CommonClassNames.config)
        .addParameter(
            "extractor", CommonClassNames.configValueExtractor
                .parameterizedBy(ClassName(packageName, configClassName))
        )
        .addStatement("return extractor.extract(config.get(%S))", configPath)
        .returns(ClassName(packageName, configClassName))
        .build()


    private data class ConfigParameter(val name: String, val type: ClassName, val defaultValue: CodeBlock?)

    private fun configType(type: KSClassDeclaration, function: KSFunctionDeclaration, vararg params: ConfigParameter): TypeSpec {
        val configClassName = type.getOuterClassesAsPrefix() + type.simpleName.getShortName() + "_" + function.simpleName.getShortName() + "_Config"
        val configType = TypeSpec.interfaceBuilder(configClassName)
            .addAnnotation(CommonClassNames.configValueExtractorAnnotation)
            .generated(JdkSchedulingGenerator::class)
            .addSuperinterface(schedulingJobConfigClassName)
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

    private fun cronConfigType(type: KSClassDeclaration, function: KSFunctionDeclaration, defaultCron: String): TypeSpec {
        val configClassName = type.getOuterClassesAsPrefix() + type.simpleName.getShortName() + "_" + function.simpleName.getShortName() + "_Config"
        val configType = TypeSpec.interfaceBuilder(configClassName)
            .addAnnotation(CommonClassNames.configValueExtractorAnnotation)
            .generated(JdkSchedulingGenerator::class)
            .addSuperinterface(schedulingJobConfigClassName)
        if (defaultCron.isBlank()) {
            configType.addFunction(FunSpec.builder("cron").addModifiers(KModifier.ABSTRACT).returns(STRING).build())
        } else {
            configType.addFunction(FunSpec.builder("cron").returns(STRING).addStatement("return %S", defaultCron).build())
        }
        return configType.build()
    }

    private fun cronConfigComponent(packageName: String, configClassName: String, configPath: String, defaultCron: String) = FunSpec.builder(configClassName)
        .addParameter("config", CommonClassNames.config)
        .addParameter("extractor", CommonClassNames.configValueExtractor.parameterizedBy(ClassName(packageName, configClassName)))
        .addStatement("val value = config.get(%S)", configPath)
        .apply {
            if (defaultCron.isNotBlank()) {
                controlFlow("if (value is %T.NullValue)", CommonClassNames.configValue) {
                    addCode("return extractor.extract(\n")
                    addCode("  %T.ObjectValue(value.origin(), mapOf(%S to %T.StringValue(value.origin(), %S)))\n", CommonClassNames.configValue, "cron", CommonClassNames.configValue, defaultCron)
                    addCode(")!!\n")
                }
            }
        }
        .controlFlow("if (value is %T.ObjectValue)", CommonClassNames.configValue) {
            addStatement("return extractor.extract(value)!!")
        }
        .controlFlow("if (value is %T.StringValue)", CommonClassNames.configValue) {
            addCode("return extractor.extract(\n")
            addCode("  %T.ObjectValue(value.origin(), mapOf(%S to %T.StringValue(value.origin(), value.value())))\n", CommonClassNames.configValue, "cron", CommonClassNames.configValue)
            addCode(")!!\n")
            nextControlFlow("else")
            addStatement(
                "throw %T.unexpectedValueType(value, %T.StringValue::class.java)",
                ClassName("io.koraframework.config.common.extractor", "ConfigValueExtractionException"),
                CommonClassNames.configValue
            )
        }
        .returns(ClassName(packageName, configClassName))
        .build()
}
