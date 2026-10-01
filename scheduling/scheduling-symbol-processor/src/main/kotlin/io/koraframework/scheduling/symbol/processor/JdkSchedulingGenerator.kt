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

    private val schedulerType = "jdk"

    companion object {
        val scheduleOnce = ClassName("io.koraframework.scheduling.jdk.annotation", "ScheduleJdkOnce")
        val scheduleWithCron = ClassName("io.koraframework.scheduling.jdk.annotation", "ScheduleJdkWithCron")
        val scheduleAtFixedRate = ClassName("io.koraframework.scheduling.jdk.annotation", "ScheduleJdkAtFixedRate")
        val scheduleWithFixedDelay = ClassName("io.koraframework.scheduling.jdk.annotation", "ScheduleJdkWithFixedDelay")
    }

    private val fixedDelayJobClassName = ClassName("io.koraframework.scheduling.jdk.job", "FixedDelayJob")
    private val fixedRateJobClassName = ClassName("io.koraframework.scheduling.jdk.job", "FixedRateJob")
    private val runOnceJobClassName = ClassName("io.koraframework.scheduling.jdk.job", "RunOnceJob")
    private val cronJobClassName = ClassName("io.koraframework.scheduling.jdk.job", "CronJob")
    private val jdkSchedulingExecutor = ClassName("io.koraframework.scheduling.jdk", "SchedulingJdkExecutor")
    private val schedulingTelemetryFactoryClassName = ClassName("io.koraframework.scheduling.common.telemetry", "SchedulingTelemetryFactory")
    private val schedulingJobConfigClassName = ClassName("io.koraframework.scheduling.common", "SchedulingJobConfig")
    private val jobTelemetryConfigClassName = ClassName("io.koraframework.scheduling.common", "SchedulingJobConfig", "JobTelemetryConfig")

    fun generate(type: KSClassDeclaration, function: KSFunctionDeclaration, builder: TypeSpec.Builder, trigger: SchedulingTrigger) {
        when (val annotationType = trigger.annotation.annotationType.resolve().toClassName()) {
            scheduleAtFixedRate -> this.generateScheduleAtFixedRate(type, function, builder, trigger)
            scheduleWithFixedDelay -> this.generateScheduleWithFixedDelay(type, function, builder, trigger)
            scheduleOnce -> this.generateScheduleOnce(type, function, builder, trigger)
            scheduleWithCron -> this.generateScheduleWithCron(type, function, builder, trigger)
            else -> throw IllegalStateException("Kora internal error: unsupported JDK scheduling annotation '$annotationType' on '${type.qualifiedName?.asString()}#${function.simpleName.asString()}()'")
        }
    }

    private fun generateScheduleWithCron(type: KSClassDeclaration, function: KSFunctionDeclaration, builder: TypeSpec.Builder, trigger: SchedulingTrigger) {
        val packageName = type.packageName.asString()
        val configName = trigger.annotation.findValue<String>("config")
        val typeClassName = type.toClassName()
        val jobFunName = type.getOuterClassesAsPrefix() + type.simpleName.getShortName() + "_" + function.simpleName.getShortName() + "_Job"
        val cron = trigger.annotation.findValue<String>("value")
        CronValidator.check(CronValidator.Dialect.JDK, cron, type, function)
        val componentFunction = FunSpec.builder(jobFunName)
            .addParameter("telemetryFactory", schedulingTelemetryFactoryClassName)
            .addParameter("service", jdkSchedulingExecutor)
            .addParameter("target", CommonClassNames.valueOf.parameterizedBy(typeClassName))
            .addParameter(zoneIdParameter())
            .returns(cronJobClassName)
            .addAnnotation(CommonClassNames.root)
            .addAnnotations(conditionalOf(type))

        if (configName.isNullOrBlank()) {
            if (cron.isNullOrBlank()) {
                throw ProcessingErrorException(missingSchedulingParameterError("ScheduleJdkWithCron", function, "value", "config"), function)
            }
            componentFunction
                .addStatement("val telemetry = telemetryFactory.get(%S, null, null, %T::class.java, %S)", schedulerType, typeClassName, function.simpleName.getShortName())
                .addStatement("val cron = %S", cron)
        } else {
            val configType = cronConfigType(type, function, cron ?: "")
            FileSpec.get(packageName, configType).writeTo(environment.codeGenerator, false, listOf(type.containingFile!!))

            componentFunction
                .addParameter("config", ClassName(packageName, configType.name!!))
                .addStatement("val telemetry = telemetryFactory.get(%S, %S, config.telemetry(), %T::class.java, %S)", schedulerType, configName, typeClassName, function.simpleName.getShortName())
                .addStatement("val cron = config.cron()")
            builder.addFunction(cronConfigComponent(packageName, configType.name!!, configName, cron ?: ""))
        }
        componentFunction
            .addStatement("return %T(telemetry, service, { target.get().%N() }, cron, zoneId, %L)", cronJobClassName, function.simpleName.getShortName(), if (configName.isNullOrBlank()) "true" else "config.enabled()")
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
            .addAnnotations(conditionalOf(type))

        if (configName.isNullOrBlank()) {
            if (period == null || period == 0L) {
                throw ProcessingErrorException(missingSchedulingParameterError("ScheduleJdkAtFixedRate", function, "period", "config"), function)
            }
            componentFunction
                .addStatement("val initialDelay = %T.of(%L, %L)", Duration::class, initialDelay, unit)
                .addStatement("val period = %T.of(%L, %L)", Duration::class, period, unit)
                .addStatement("val telemetry = telemetryFactory.get(%S, null, null, %T::class.java, %S)", schedulerType, typeClassName, function.simpleName.getShortName())
        } else {
            val configType = configType(
                type, function,
                ConfigParameter("period", Duration::class.asClassName(), period?.let { CodeBlock.of("%T.of(%L, %L)", Duration::class, it, unit) }),
                ConfigParameter("initialDelay", Duration::class.asClassName(), CodeBlock.of("%T.of(%L, %L)", Duration::class, initialDelay, unit)),
            )
            FileSpec.get(packageName, configType).writeTo(environment.codeGenerator, false, listOf(type.containingFile!!))

            componentFunction
                .addParameter("config", ClassName(packageName, configType.name!!))
                .addStatement("val telemetry = telemetryFactory.get(%S, %S, config.telemetry(), %T::class.java, %S)", schedulerType, configName, typeClassName, function.simpleName.getShortName())
                .addStatement("val period = config.period()")
                .addStatement("val initialDelay = config.initialDelay()")
            builder.addFunction(configComponent(packageName, configType.name!!, configName))
        }
        componentFunction
            .addStatement("return %T(telemetry, service, { target.get().%N() }, initialDelay, period, %L)", fixedRateJobClassName, function.simpleName.getShortName(), if (configName.isNullOrBlank()) "true" else "config.enabled()")
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
            .addAnnotations(conditionalOf(type))

        if (configName.isNullOrBlank()) {
            if (delay == null || delay == 0L) {
                throw ProcessingErrorException(missingSchedulingParameterError("ScheduleJdkWithFixedDelay", function, "delay", "config"), function)
            }
            componentFunction
                .addStatement("val telemetry = telemetryFactory.get(%S, null, null, %T::class.java, %S)", schedulerType, typeClassName, function.simpleName.getShortName())
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
                .addStatement("val telemetry = telemetryFactory.get(%S, %S, config.telemetry(), %T::class.java, %S)", schedulerType, configName, typeClassName, function.simpleName.getShortName())
                .addStatement("val delay = config.delay()")
                .addStatement("val initialDelay = config.initialDelay()")
            builder.addFunction(configComponent(packageName, configType.name!!, configName))
        }
        componentFunction
            .addStatement("return %T(telemetry, service, { target.get().%N() }, initialDelay, delay, %L)", fixedDelayJobClassName, function.simpleName.getShortName(), if (configName.isNullOrBlank()) "true" else "config.enabled()")
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
            .addAnnotations(conditionalOf(type))

        if (configName.isNullOrBlank()) {
            if (delay == null || delay == 0L) {
                throw ProcessingErrorException(missingSchedulingParameterError("ScheduleJdkOnce", function, "delay", "config"), function)
            }
            componentFunction
                .addStatement("val telemetry = telemetryFactory.get(%S, null, null, %T::class.java, %S)", schedulerType, typeClassName, function.simpleName.getShortName())
                .addStatement("val delay = %T.of(%L, %L)", Duration::class, delay, unit)
        } else {
            val configType = configType(
                type, function,
                ConfigParameter("delay", Duration::class.asClassName(), delay?.let { CodeBlock.of("%T.of(%L, %L)", Duration::class, it, unit) })
            )
            FileSpec.get(packageName, configType).writeTo(environment.codeGenerator, false, listOf(type.containingFile!!))

            componentFunction
                .addParameter("config", ClassName(packageName, configType.name!!))
                .addStatement("val telemetry = telemetryFactory.get(%S, %S, config.telemetry(), %T::class.java, %S)", schedulerType, configName, typeClassName, function.simpleName.getShortName())
                .addStatement("val delay = config.delay()")
            builder.addFunction(configComponent(packageName, configType.name!!, configName))
        }
        componentFunction
            .addStatement("return %T(telemetry, service, { target.get().%N() }, delay, %L)", runOnceJobClassName, function.simpleName.getShortName(), if (configName.isNullOrBlank()) "true" else "config.enabled()")
        builder.addFunction(componentFunction.build())
    }

    private fun configComponent(packageName: String, configClassName: String, configPath: String) = FunSpec.builder(configClassName)
        .addParameter("config", CommonClassNames.config)
        .addParameter(
            "mapper", CommonClassNames.configValueMapper
                .parameterizedBy(ClassName(packageName, configClassName))
        )
        .addStatement("return mapper.mapOrThrow(config.get(%S))", configPath)
        .returns(ClassName(packageName, configClassName))
        .build()


    private data class ConfigParameter(val name: String, val type: ClassName, val defaultValue: CodeBlock?)

    private fun configType(type: KSClassDeclaration, function: KSFunctionDeclaration, vararg params: ConfigParameter): TypeSpec {
        val configClassName = type.getOuterClassesAsPrefix() + type.simpleName.getShortName() + "_" + function.simpleName.getShortName() + "_Config"
        val configType = TypeSpec.interfaceBuilder(configClassName)
            .addAnnotation(CommonClassNames.configMapperAnnotation)
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
            .addAnnotation(CommonClassNames.configMapperAnnotation)
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
        .addParameter("mapper", CommonClassNames.configValueMapper.parameterizedBy(ClassName(packageName, configClassName)))
        .addStatement("val value = config.get(%S)", configPath)
        .apply {
            if (defaultCron.isNotBlank()) {
                controlFlow("if (value is %T.NullValue)", CommonClassNames.configValue) {
                    addCode("return mapper.mapOrThrow(\n")
                    addCode("  %T.ObjectValue(value.origin(), mapOf(%S to %T.StringValue(value.origin(), %S)))\n", CommonClassNames.configValue, "cron", CommonClassNames.configValue, defaultCron)
                    addCode(")!!\n")
                }
            }
        }
        .controlFlow("if (value is %T.ObjectValue)", CommonClassNames.configValue) {
            addStatement("return mapper.mapOrThrow(value)!!")
        }
        .controlFlow("if (value is %T.StringValue)", CommonClassNames.configValue) {
            addCode("return mapper.mapOrThrow(\n")
            addCode("  %T.ObjectValue(value.origin(), mapOf(%S to %T.StringValue(value.origin(), value.value())))\n", CommonClassNames.configValue, "cron", CommonClassNames.configValue)
            addCode(")!!\n")
            nextControlFlow("else")
            addStatement(
                "throw %T.unexpectedValueType(value, %T.StringValue::class.java)",
                ClassName("io.koraframework.config.common.exception", "ConfigValueException"),
                CommonClassNames.configValue
            )
        }
        .returns(ClassName(packageName, configClassName))
        .build()

    private fun missingSchedulingParameterError(
        annotationName: String,
        function: KSFunctionDeclaration,
        directParameter: String,
        configParameter: String
    ): String {
        return """
            Invalid `@$annotationName` configuration on `${function.qualifiedName?.asString()}`.

            The annotation must define either `$directParameter` directly or `$configParameter` with a config path.
            A zero or blank `$directParameter` is treated as missing.

            Fix: set `$directParameter` on the annotation, or set `$configParameter` and provide scheduling settings in application config.
        """.trimIndent()
    }
}
