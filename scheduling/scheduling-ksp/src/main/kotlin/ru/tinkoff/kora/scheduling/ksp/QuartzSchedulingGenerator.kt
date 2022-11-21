package ru.tinkoff.kora.scheduling.ksp

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValue
import ru.tinkoff.kora.ksp.common.getOuterClassesAsPrefix
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.writeTagValue

class QuartzSchedulingGenerator(val env: SymbolProcessorEnvironment) {
    private val koraQuartzJobClassName: ClassName = ClassName("ru.tinkoff.kora.scheduling.quartz", "KoraQuartzJob")
    private val schedulingTelemetryClassName: ClassName = ClassName("ru.tinkoff.kora.scheduling.common.telemetry", "SchedulingTelemetry")
    private val schedulingTelemetryFactoryClassName: ClassName = ClassName("ru.tinkoff.kora.scheduling.common.telemetry", "SchedulingTelemetryFactory")
    private val triggerClassName: ClassName = ClassName("org.quartz", "Trigger")
    private val schedulerClassName: ClassName = ClassName("org.quartz", "Scheduler")
    private val triggerBuilderClassName: ClassName = ClassName("org.quartz", "TriggerBuilder")
    private val cronScheduleBuilderClassName: ClassName = ClassName("org.quartz", "CronScheduleBuilder")


    fun generate(type: KSClassDeclaration, function: KSFunctionDeclaration, builder: TypeSpec.Builder, trigger: SchedulingTrigger) {
        val jobClassName = generateJobClass(type, function)
        val typeClassName = type.toClassName()
        val component = FunSpec.builder("_" + type.simpleName.getShortName() + "_" + function.simpleName.getShortName() + "_Job")
            .returns(jobClassName)
            .addParameter("telemetryFactory", schedulingTelemetryFactoryClassName)
            .addParameter("target", typeClassName);

        when (trigger.annotation.shortName.getShortName()) {
            "ScheduleWithTrigger" -> {
                val tag = trigger.annotation.findValue<KSAnnotation>("value")!!
                val tagAnnotationSpec = AnnotationSpec.builder(Tag::class)
                    .addMember(tag.findValue<List<KSType>>("value")!!.writeTagValue("value"))
                    .build()

                val triggerParameter = ParameterSpec.builder("trigger", triggerClassName)
                    .addAnnotation(tagAnnotationSpec)
                    .build()
                component.addParameter(triggerParameter)
            }

            "ScheduleWithCron" -> {
                val identity = trigger.annotation.findValue<String>("identity").let {
                    if (it.isNullOrBlank()) {
                        type.qualifiedName!!.asString() + "#" + function.simpleName.getShortName()
                    } else {
                        it
                    }
                }
                val cron = trigger.annotation.findValue<String>("value") ?: ""
                var cronSchedule = CodeBlock.of("%S", cron)
                val configPath = trigger.annotation.findValue<String>("config")
                if (!configPath.isNullOrBlank()) {
                    val configClassName = this.generateCronConfigRecord(type, function, cron)
                    val b = FunSpec.builder(configClassName.simpleName)
                        .returns(configClassName)
                        .addParameter("config", ClassName("com.typesafe.config", "Config"))
                    if (cron.isNotBlank()) {
                        b.addCode("if (!config.hasPath(%S)) {\n  return %T(%S);\n}\n", configPath, configClassName, cron)
                    }
                    b.addCode(
                        """
                            val value = config.getValue(%S)
                            if (value.valueType() != com.typesafe.config.ConfigValueType.STRING) {
                              throw ru.tinkoff.kora.config.common.extractor.ConfigValueExtractionException.unexpectedValueType(value, com.typesafe.config.ConfigValueType.STRING)
                            }
                            return %T(value.unwrapped().toString());
                        """.trimIndent(), configPath, configClassName
                    )
                    builder.addFunction(b.build())
                    component.addParameter("config", configClassName);
                    cronSchedule = CodeBlock.of("config.cron")
                }
                component.addCode(
                    """
                val trigger = %T.newTrigger()
                  .withIdentity(%S)
                  .withSchedule(%T.cronSchedule(%L))
                  .build();
                """.trimIndent() + "\n", triggerBuilderClassName, identity, cronScheduleBuilderClassName, cronSchedule.toString()
                );
            }
        }
        component.addCode("val telemetry = telemetryFactory.get(%T::class.java, %S);\n", typeClassName, function.simpleName.getShortName())

        component
            .addCode("return %T(telemetry, target, trigger);\n", jobClassName)

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
        val typeSpec: TypeSpec = TypeSpec.classBuilder(className)
            .superclass(koraQuartzJobClassName)
            .addSuperclassConstructorParameter(CodeBlock.of("telemetry"))
            .addSuperclassConstructorParameter(callJob)
            .addSuperclassConstructorParameter(CodeBlock.of("trigger"))
            .addProperty(
                PropertySpec.builder("target", typeClassName, KModifier.PRIVATE, KModifier.FINAL)
                    .initializer("target")
                    .build()
            )
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("telemetry", schedulingTelemetryClassName)
                    .addParameter("target", typeClassName)
                    .addParameter("trigger", triggerClassName)
                    .build()
            )
            .build()
        FileSpec.get(packageName, typeSpec).writeTo(env.codeGenerator, false, listOf(type.containingFile!!))
        return ClassName(packageName, className)
    }

    private fun generateCronConfigRecord(type: KSClassDeclaration, function: KSFunctionDeclaration, defaultCron: String): ClassName {
        val configClassName = type.getOuterClassesAsPrefix() + type.simpleName.getShortName() + "_" + function.simpleName.getShortName() + "_CronConfig"
        val constructor = FunSpec.constructorBuilder()
        if (defaultCron.isBlank()) {
            constructor.addParameter("cron", STRING)
        } else {
            constructor.addParameter(
                ParameterSpec.builder("cron", STRING)
                    .defaultValue(CodeBlock.of("%S", defaultCron))
                    .build()
            )
        }
        val configType = TypeSpec.classBuilder(configClassName)
            .addModifiers(KModifier.PUBLIC, KModifier.DATA)
            .addProperty(
                PropertySpec.builder("cron", STRING)
                    .initializer("cron")
                    .build()
            )
            .primaryConstructor(constructor.build())
            .build()
        FileSpec.get(type.packageName.asString(), configType).writeTo(env.codeGenerator, false, listOf(type.containingFile!!))
        return ClassName(type.packageName.asString(), configClassName)
    }
}
