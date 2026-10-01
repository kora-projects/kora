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

class DbSchedulingGenerator(private val environment: SymbolProcessorEnvironment) {

    private val schedulerType = "dbscheduler"

    companion object {
        val scheduleOnce = ClassName("io.koraframework.scheduling.db.scheduler.annotation", "ScheduleDbOnce")
        val scheduleWithCron = ClassName("io.koraframework.scheduling.db.scheduler.annotation", "ScheduleDbWithCron")
        val scheduleWithFixedDelay = ClassName("io.koraframework.scheduling.db.scheduler.annotation", "ScheduleDbWithFixedDelay")

        // task_name column length in the bundled db/kora/scheduling-db-scheduler/schema scripts
        private const val MAX_NAME_LENGTH = 350
    }

    private val dbScheduledJobClassName = ClassName("io.koraframework.scheduling.db.scheduler.job", "DbSchedulerJob")
    private val cronJobClassName = ClassName("io.koraframework.scheduling.db.scheduler.job", "CronJob")
    private val fixedDelayJobClassName = ClassName("io.koraframework.scheduling.db.scheduler.job", "FixedDelayJob")
    private val runOnceJobClassName = ClassName("io.koraframework.scheduling.db.scheduler.job", "RunOnceJob")
    private val schedulingTelemetryFactoryClassName = ClassName("io.koraframework.scheduling.common.telemetry", "SchedulingTelemetryFactory")
    private val schedulingJobConfigClassName = ClassName("io.koraframework.scheduling.common", "SchedulingJobConfig")

    fun generate(type: KSClassDeclaration, function: KSFunctionDeclaration, builder: TypeSpec.Builder, trigger: SchedulingTrigger) {
        when (trigger.annotation.annotationType.resolve().toClassName()) {
            scheduleOnce -> this.generateScheduleOnce(type, function, builder, trigger)
            scheduleWithCron -> this.generateScheduleWithCron(type, function, builder, trigger)
            scheduleWithFixedDelay -> this.generateScheduleWithFixedDelay(type, function, builder, trigger)
        }
    }

    private fun generateScheduleWithCron(type: KSClassDeclaration, function: KSFunctionDeclaration, builder: TypeSpec.Builder, trigger: SchedulingTrigger) {
        val packageName = type.packageName.asString()
        val configName = trigger.annotation.findValue<String>("config")
        val cron = trigger.annotation.findValue<String>("value")
        CronValidator.check(CronValidator.Dialect.DB, cron, type, function)
        val name = name(type, function, trigger.annotation.findValue<String>("name"))
        val component = component(type, function)
            .addParameter(zoneIdParameter())

        if (configName.isNullOrBlank()) {
            if (cron.isNullOrBlank()) {
                throw ProcessingErrorException("Either value() or config() annotation parameter must be provided", function)
            }
            component
                .addCode("val telemetry = telemetryFactory.get(%S, null, null, %T::class.java, %S);\n", schedulerType, type.toClassName(), function.simpleName.getShortName())
                .addCode("return %T(telemetry, { target.get().%N() }, %S, %S, zoneId, true);\n", cronJobClassName, function.simpleName.getShortName(), name, cron)
        } else {
            val configType = cronConfigType(type, function, cron ?: "")
            FileSpec.get(packageName, configType).writeTo(environment.codeGenerator, false, listOf(type.containingFile!!))
            builder.addFunction(cronConfigComponent(packageName, configType.name!!, configName, cron ?: ""))
            component.addParameter("config", ClassName(packageName, configType.name!!))
            component
                .addCode("val telemetry = telemetryFactory.get(%S, %S, config.telemetry(), %T::class.java, %S);\n", schedulerType, configName, type.toClassName(), function.simpleName.getShortName())
                .addCode("return %T(telemetry, { target.get().%N() }, %S, config.cron(), zoneId, config.enabled());\n", cronJobClassName, function.simpleName.getShortName(), name)
        }
        builder.addFunction(component.build())
    }

    private fun generateScheduleWithFixedDelay(type: KSClassDeclaration, function: KSFunctionDeclaration, builder: TypeSpec.Builder, trigger: SchedulingTrigger) {
        val packageName = type.packageName.asString()
        val configName = trigger.annotation.findValue<String>("config")
        val delay = trigger.annotation.findValue<Long>("delay")
        val initialDelay = trigger.annotation.findValue<Long>("initialDelay") ?: 0
        val unit = trigger.annotation.findEnumValue("unit")!!
        val name = name(type, function, trigger.annotation.findValue<String>("name"))
        val component = component(type, function)

        if (configName.isNullOrBlank()) {
            if (delay == null || delay == 0L) {
                throw ProcessingErrorException("Either delay() or config() annotation parameter must be provided", function)
            }
            component
                .addCode("val telemetry = telemetryFactory.get(%S, null, null, %T::class.java, %S);\n", schedulerType, type.toClassName(), function.simpleName.getShortName())
                .addCode("val initialDelay = %T.of(%L, %L);\n", Duration::class, initialDelay, unit)
                .addCode("val delay = %T.of(%L, %L);\n", Duration::class, delay, unit)
                .addCode("return %T(telemetry, { target.get().%N() }, %S, initialDelay, delay, true);\n", fixedDelayJobClassName, function.simpleName.getShortName(), name)
        } else {
            val configType = configType(
                type, function,
                ConfigParameter("delay", Duration::class.asClassName(), delay?.let { CodeBlock.of("%T.of(%L, %L)", Duration::class, it, unit) }),
                ConfigParameter("initialDelay", Duration::class.asClassName(), CodeBlock.of("%T.of(%L, %L)", Duration::class, initialDelay, unit)),
            )
            FileSpec.get(packageName, configType).writeTo(environment.codeGenerator, false, listOf(type.containingFile!!))
            builder.addFunction(configComponent(packageName, configType.name!!, configName))
            component.addParameter("config", ClassName(packageName, configType.name!!))
            component
                .addCode("val telemetry = telemetryFactory.get(%S, %S, config.telemetry(), %T::class.java, %S);\n", schedulerType, configName, type.toClassName(), function.simpleName.getShortName())
                .addCode("return %T(telemetry, { target.get().%N() }, %S, config.initialDelay(), config.delay(), config.enabled());\n", fixedDelayJobClassName, function.simpleName.getShortName(), name)
        }
        builder.addFunction(component.build())
    }

    private fun generateScheduleOnce(type: KSClassDeclaration, function: KSFunctionDeclaration, builder: TypeSpec.Builder, trigger: SchedulingTrigger) {
        val packageName = type.packageName.asString()
        val configName = trigger.annotation.findValue<String>("config")
        val delay = trigger.annotation.findValue<Long>("delay")
        val unit = trigger.annotation.findEnumValue("unit")!!
        val name = name(type, function, trigger.annotation.findValue<String>("name"))
        val component = component(type, function)

        if (configName.isNullOrBlank()) {
            if (delay == null || delay == 0L) {
                throw ProcessingErrorException("Either delay() or config() annotation parameter must be provided", function)
            }
            component
                .addCode("val telemetry = telemetryFactory.get(%S, null, null, %T::class.java, %S);\n", schedulerType, type.toClassName(), function.simpleName.getShortName())
                .addCode("val delay = %T.of(%L, %L);\n", Duration::class, delay, unit)
                .addCode("return %T(telemetry, { target.get().%N() }, %S, delay, true);\n", runOnceJobClassName, function.simpleName.getShortName(), name)
        } else {
            val configType = configType(
                type, function,
                ConfigParameter("delay", Duration::class.asClassName(), delay?.let { CodeBlock.of("%T.of(%L, %L)", Duration::class, it, unit) }),
            )
            FileSpec.get(packageName, configType).writeTo(environment.codeGenerator, false, listOf(type.containingFile!!))
            builder.addFunction(configComponent(packageName, configType.name!!, configName))
            component.addParameter("config", ClassName(packageName, configType.name!!))
            component
                .addCode("val telemetry = telemetryFactory.get(%S, %S, config.telemetry(), %T::class.java, %S);\n", schedulerType, configName, type.toClassName(), function.simpleName.getShortName())
                .addCode("return %T(telemetry, { target.get().%N() }, %S, config.delay(), config.enabled());\n", runOnceJobClassName, function.simpleName.getShortName(), name)
        }
        builder.addFunction(component.build())
    }

    private fun component(type: KSClassDeclaration, function: KSFunctionDeclaration): FunSpec.Builder {
        val typeClassName = type.toClassName()
        val jobFunName = type.getOuterClassesAsPrefix() + type.simpleName.getShortName() + "_" + function.simpleName.getShortName() + "_Job"
        return FunSpec.builder(jobFunName)
            .addParameter("telemetryFactory", schedulingTelemetryFactoryClassName)
            .addParameter("target", CommonClassNames.valueOf.parameterizedBy(typeClassName))
            .returns(dbScheduledJobClassName)
            .addAnnotation(CommonClassNames.root)
            .addAnnotations(conditionalOf(type))
    }

    private fun name(type: KSClassDeclaration, function: KSFunctionDeclaration, name: String?): String {
        val result = if (name.isNullOrBlank()) {
            type.qualifiedName!!.asString() + "#" + function.simpleName.getShortName()
        } else {
            name
        }
        if (result.length > MAX_NAME_LENGTH) {
            throw ProcessingErrorException(
                "Database scheduled job name '$result' is ${result.length} characters long, maximum is $MAX_NAME_LENGTH. Set a shorter name() in the annotation.",
                function
            )
        }
        return result
    }

    private fun configComponent(packageName: String, configClassName: String, configPath: String) = FunSpec.builder(configClassName)
        .addParameter("config", CommonClassNames.config)
        .addParameter("extractor", CommonClassNames.configValueMapper.parameterizedBy(ClassName(packageName, configClassName)))
        .addCode("val configValue = config.get(%S);\n", configPath)
        .addStatement("return extractor.mapOrThrow(configValue)")
        .returns(ClassName(packageName, configClassName))
        .build()

    private fun cronConfigComponent(packageName: String, configClassName: String, configPath: String, defaultCron: String) = FunSpec.builder(configClassName)
        .addParameter("config", CommonClassNames.config)
        .addParameter("extractor", CommonClassNames.configValueMapper.parameterizedBy(ClassName(packageName, configClassName)))
        .addCode("val value = config.get(%S);\n", configPath)
        .apply {
            if (defaultCron.isNotBlank()) {
                controlFlow("if (value is %T.NullValue)", CommonClassNames.configValue) {
                    addCode("return extractor.mapOrThrow(\n")
                    addCode("  %T.ObjectValue(value.origin(), mapOf(%S to %T.StringValue(value.origin(), %S)))\n", CommonClassNames.configValue, "cron", CommonClassNames.configValue, defaultCron)
                    addCode(")\n")
                }
            }
        }
        .controlFlow("if (value is %T.ObjectValue)", CommonClassNames.configValue) {
            addStatement("return extractor.mapOrThrow(value)")
        }
        .controlFlow("if (value is %T.StringValue)", CommonClassNames.configValue) {
            addCode("return extractor.mapOrThrow(\n")
            addCode("  %T.ObjectValue(value.origin(), mapOf(%S to %T.StringValue(value.origin(), value.value())))\n", CommonClassNames.configValue, "cron", CommonClassNames.configValue)
            addCode(")\n")
            nextControlFlow("else")
            addStatement("throw %T.unexpectedValueType(value, %T.StringValue::class.java)", CommonClassNames.configValueException, CommonClassNames.configValue)
        }
        .returns(ClassName(packageName, configClassName))
        .build()

    private data class ConfigParameter(val name: String, val type: TypeName, val defaultValue: CodeBlock?)

    private fun configType(type: KSClassDeclaration, function: KSFunctionDeclaration, vararg params: ConfigParameter): TypeSpec {
        val configClassName = type.getOuterClassesAsPrefix() + type.simpleName.getShortName() + "_" + function.simpleName.getShortName() + "_Config"
        val configType = TypeSpec.interfaceBuilder(configClassName)
            .addAnnotation(CommonClassNames.configMapperAnnotation)
            .generated(DbSchedulingGenerator::class)
            .addSuperinterface(schedulingJobConfigClassName)
        for (param in params) {
            configType.addFunction(
                FunSpec.builder(param.name)
                    .returns(param.type)
                    .apply {
                        param.defaultValue?.let { addStatement("return %L", it) }
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
            .generated(DbSchedulingGenerator::class)
            .addSuperinterface(schedulingJobConfigClassName)
        if (defaultCron.isBlank()) {
            configType.addFunction(FunSpec.builder("cron").addModifiers(KModifier.ABSTRACT).returns(STRING).build())
        } else {
            configType.addFunction(FunSpec.builder("cron").returns(STRING).addStatement("return %S", defaultCron).build())
        }
        return configType.build()
    }
}
