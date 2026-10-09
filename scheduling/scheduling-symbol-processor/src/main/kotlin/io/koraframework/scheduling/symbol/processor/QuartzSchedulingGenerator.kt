package io.koraframework.scheduling.symbol.processor

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import io.koraframework.ksp.common.AnnotationUtils.findValue
import io.koraframework.ksp.common.AnnotationUtils.isAnnotationPresent
import io.koraframework.ksp.common.CommonClassNames
import io.koraframework.ksp.common.KotlinPoetUtils.controlFlow
import io.koraframework.ksp.common.KspCommonUtils.generated
import io.koraframework.ksp.common.TagUtils.addTag
import io.koraframework.ksp.common.getOuterClassesAsPrefix

class QuartzSchedulingGenerator(val env: SymbolProcessorEnvironment) {

    private val schedulerType = "quartz"

    companion object {
        val scheduleWithTrigger = ClassName("io.koraframework.scheduling.quartz.annotation", "ScheduleQuartzWithTrigger")
        val scheduleWithCron = ClassName("io.koraframework.scheduling.quartz.annotation", "ScheduleQuartzWithCron")
    }

    private val koraQuartzJobClassName: ClassName = ClassName("io.koraframework.scheduling.quartz", "KoraQuartzJob")
    private val schedulingTelemetryClassName: ClassName = ClassName("io.koraframework.scheduling.common.telemetry", "SchedulingTelemetry")
    private val schedulingTelemetryFactoryClassName: ClassName = ClassName("io.koraframework.scheduling.common.telemetry", "SchedulingTelemetryFactory")
    private val schedulingJobConfigClassName = ClassName("io.koraframework.scheduling.common", "SchedulingJobConfig")
    private val jobTelemetryConfigClassName = ClassName("io.koraframework.scheduling.common", "SchedulingJobConfig", "JobTelemetryConfig")
    private val triggerClassName: ClassName = ClassName("org.quartz", "Trigger")
    private val schedulerClassName: ClassName = ClassName("org.quartz", "Scheduler")
    private val triggerBuilderClassName: ClassName = ClassName("org.quartz", "TriggerBuilder")
    private val quartzCronUtilsClassName: ClassName = ClassName("io.koraframework.scheduling.quartz.util", "QuartzCronUtils")

    fun generate(type: KSClassDeclaration, function: KSFunctionDeclaration, builder: TypeSpec.Builder, trigger: SchedulingTrigger) {
        val jobClassName = generateJobClass(type, function)
        val typeClassName = type.toClassName()
        val component = FunSpec.builder(moduleFunctionName(type.getOuterClassesAsPrefix() + type.simpleName.getShortName() + "_" + function.simpleName.getShortName() + "_Job"))
            .returns(jobClassName)
            .addParameter("telemetryFactory", schedulingTelemetryFactoryClassName)
            .addParameter("target", typeClassName)
            .addAnnotations(conditionalOf(type))

        when (val annotationType = trigger.annotation.annotationType.resolve().toClassName()) {
            scheduleWithTrigger -> {
                val tag = trigger.annotation.findValue<KSType>("value")!!
                    .declaration.let { it as KSClassDeclaration }
                    .toClassName()

                val triggerParameter = ParameterSpec.builder("trigger", triggerClassName)
                    .addTag(tag)
                    .build()
                component.addParameter(triggerParameter)
                component.addStatement("val telemetry = telemetryFactory.get(%S, null, null, %T::class.java, %S)", schedulerType, typeClassName, function.simpleName.getShortName())
                component.addStatement("val triggers = listOf<%T>(trigger)", triggerClassName)
            }

            scheduleWithCron -> {
                val identity = trigger.annotation.findValue<String>("identity").let {
                    if (it.isNullOrBlank()) {
                        type.qualifiedName!!.asString() + "#" + function.simpleName.getShortName()
                    } else {
                        it
                    }
                }
                val cron = trigger.annotation.findValue<String>("value") ?: ""
                CronValidator.check(CronValidator.Dialect.QUARTZ, cron, type, function)
                var cronSchedule = CodeBlock.of("%S", cron)
                val configPath = trigger.annotation.findValue<String>("config")
                if (!configPath.isNullOrBlank()) {
                    val configClassName = this.generateCronConfigRecord(type, function, cron)
                    val b = FunSpec.builder(moduleFunctionName(configClassName.simpleName))
                        .returns(configClassName)
                        .addParameter("config", CommonClassNames.config)
                        .addParameter("mapper", CommonClassNames.configValueMapper.parameterizedBy(configClassName))
                    if (cron.isNotBlank()) {
                        b.addStatement("val value = config.get(%S)", configPath)
                        b.controlFlow("if (value is %T.NullValue)", CommonClassNames.configValue) {
                            addCode("return mapper.mapOrThrow(\n")
                            addCode("  %T.ObjectValue(value.origin(), mapOf(%S to %T.StringValue(value.origin(), %S)))\n", CommonClassNames.configValue, "cron", CommonClassNames.configValue, cron)
                            addCode(")!!\n")
                        }
                    } else {
                        b.addStatement("val value = config.get(%S)!!", configPath)
                    }
                    b.controlFlow("if (value is %T.ObjectValue)", CommonClassNames.configValue) {
                        addStatement("return mapper.mapOrThrow(value)!!")
                    }
                    b.controlFlow("if (value is %T.StringValue)", CommonClassNames.configValue) {
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

                    builder.addFunction(b.build())
                    component.addParameter("config", configClassName)
                    cronSchedule = CodeBlock.of("config.cron()")
                }
                component.addParameter(zoneIdParameter())
                component.addCode(
                    """
                val trigger = %T.newTrigger()
                  .withIdentity(%S)
                  .withSchedule(%T.cronSchedule(%L, zoneId, %T::class.java, %S))
                  .build()
                """.trimIndent() + "\n", triggerBuilderClassName, identity, quartzCronUtilsClassName, cronSchedule.toString(), typeClassName, function.simpleName.getShortName()
                )
                if (!configPath.isNullOrBlank()) {
                    component.addStatement("val telemetry = telemetryFactory.get(%S, %S, config.telemetry(), %T::class.java, %S)", schedulerType, configPath, typeClassName, function.simpleName.getShortName())
                    component.addStatement("val triggers = if (config.enabled()) listOf<%T>(trigger) else listOf()", triggerClassName)
                } else {
                    component.addStatement("val telemetry = telemetryFactory.get(%S, null, null, %T::class.java, %S)", schedulerType, typeClassName, function.simpleName.getShortName())
                    component.addStatement("val triggers = listOf<%T>(trigger)", triggerClassName)
                }
            }

            else -> throw IllegalStateException("Kora internal error: unsupported Quartz scheduling annotation '$annotationType' on '${type.qualifiedName?.asString()}#${function.simpleName.asString()}()'")
        }

        component
            .addCode("return %T(telemetry, target, triggers)\n", jobClassName)

        builder.addFunction(component.build())
    }


    private fun generateJobClass(type: KSClassDeclaration, method: KSFunctionDeclaration): ClassName {
        val className: String = type.getOuterClassesAsPrefix() + type.simpleName.getShortName() + "_" + method.simpleName.getShortName() + "_Job"
        val packageName: String = type.packageName.asString()
        val callJob = if (method.parameters.none { !it.hasDefault }) {
            CodeBlock.of("{ ctx -> target.%L() }", method.simpleName.getShortName())
        } else {
            CodeBlock.of("{ ctx -> target.%L(ctx) }", method.simpleName.getShortName())
        }
        val typeClassName = type.toClassName()
        val typeSpec = TypeSpec.classBuilder(className)
            .generated(QuartzSchedulingGenerator::class)
            .superclass(koraQuartzJobClassName)
            .addSuperclassConstructorParameter(CodeBlock.of("telemetry"))
            .addSuperclassConstructorParameter(callJob)
            .addSuperclassConstructorParameter(CodeBlock.of("triggers"))
            .addProperty(
                PropertySpec.builder("target", typeClassName, KModifier.PRIVATE, KModifier.FINAL)
                    .initializer("target")
                    .build()
            )
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("telemetry", schedulingTelemetryClassName)
                    .addParameter("target", typeClassName)
                    .addParameter("triggers", LIST.parameterizedBy(triggerClassName))
                    .build()
            )
        val quartzDisallowConcurrentExecution = ClassName("org.quartz", "DisallowConcurrentExecution")
        val koraDisallowConcurrentExecution = ClassName("io.koraframework.scheduling.quartz.annotation", "DisallowConcurrentExecution")
        if (type.isAnnotationPresent(quartzDisallowConcurrentExecution) || method.isAnnotationPresent(koraDisallowConcurrentExecution)) {
            typeSpec.addAnnotation(quartzDisallowConcurrentExecution)
        }
        val quartzPersistJobData = ClassName("org.quartz", "PersistJobDataAfterExecution")
        val koraPersistJobData = ClassName("io.koraframework.scheduling.quartz.annotation", "PersistJobDataAfterExecution")
        if (type.isAnnotationPresent(quartzPersistJobData) || method.isAnnotationPresent(koraPersistJobData)) {
            typeSpec.addAnnotation(quartzPersistJobData)
        }

        FileSpec.get(packageName, typeSpec.build()).writeTo(env.codeGenerator, false, listOf(type.containingFile!!))
        return ClassName(packageName, className)
    }

    private fun generateCronConfigRecord(type: KSClassDeclaration, function: KSFunctionDeclaration, defaultCron: String): ClassName {
        val configClassName = type.getOuterClassesAsPrefix() + type.simpleName.getShortName() + "_" + function.simpleName.getShortName() + "_CronConfig"
        val configType = TypeSpec.interfaceBuilder(configClassName)
            .addAnnotation(CommonClassNames.configMapperAnnotation)
            .generated(QuartzSchedulingGenerator::class)
            .addSuperinterface(schedulingJobConfigClassName)

        if (defaultCron.isBlank()) {
            configType.addFunction(FunSpec.builder("cron").addModifiers(KModifier.ABSTRACT).returns(STRING).build())
        } else {
            configType.addFunction(FunSpec.builder("cron").returns(STRING).addStatement("return %S", defaultCron).build())
        }

        FileSpec.get(type.packageName.asString(), configType.build()).writeTo(env.codeGenerator, false, listOf(type.containingFile!!))
        return ClassName(type.packageName.asString(), configClassName)

    }
}
