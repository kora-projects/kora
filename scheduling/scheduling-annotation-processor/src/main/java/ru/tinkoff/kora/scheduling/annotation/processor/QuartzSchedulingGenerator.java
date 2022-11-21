package ru.tinkoff.kora.scheduling.annotation.processor;

import com.squareup.javapoet.*;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.annotation.processor.common.RecordClassBuilder;

import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Predicate;

public class QuartzSchedulingGenerator {
    private static final ClassName koraQuartzJobClassName = ClassName.get("ru.tinkoff.kora.scheduling.quartz", "KoraQuartzJob");
    private static final ClassName schedulingTelemetryClassName = ClassName.get("ru.tinkoff.kora.scheduling.common.telemetry", "SchedulingTelemetry");
    private static final ClassName schedulingTelemetryFactoryClassName = ClassName.get("ru.tinkoff.kora.scheduling.common.telemetry", "SchedulingTelemetryFactory");
    private static final ClassName triggerClassName = ClassName.get("org.quartz", "Trigger");
    private static final ClassName schedulerClassName = ClassName.get("org.quartz", "Scheduler");
    private static final ClassName triggerBuilderClassName = ClassName.get("org.quartz", "TriggerBuilder");
    private static final ClassName cronScheduleBuilderClassName = ClassName.get("org.quartz", "CronScheduleBuilder");
    private final Types types;
    private final Elements elements;
    private final Filer filer;
    private final TypeMirror scheduleWithTriggerTypeMirror;
    private final TypeMirror scheduleWithCronTypeMirror;

    public QuartzSchedulingGenerator(ProcessingEnvironment processingEnv) {
        this.types = processingEnv.getTypeUtils();
        this.elements = processingEnv.getElementUtils();
        this.filer = processingEnv.getFiler();

        this.scheduleWithTriggerTypeMirror = this.elements.getTypeElement("ru.tinkoff.kora.scheduling.quartz.ScheduleWithTrigger").asType();
        this.scheduleWithCronTypeMirror = this.elements.getTypeElement("ru.tinkoff.kora.scheduling.quartz.ScheduleWithCron").asType();
    }

    public void generate(TypeElement type, ExecutableElement method, TypeSpec.Builder module, SchedulingTrigger trigger) throws IOException {
        var jobClassName = this.generateJobClass(type, method);
        var typeMirror = type.asType();

        var component = MethodSpec.methodBuilder(type.getSimpleName() + "_" + method.getSimpleName() + "_Job")
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .returns(jobClassName)
            .addParameter(schedulingTelemetryFactoryClassName, "telemetryFactory")
            .addParameter(TypeName.get(typeMirror), "object");


        if (this.types.isSameType(trigger.triggerAnnotation().getAnnotationType(), this.scheduleWithTriggerTypeMirror)) {
            var tag = CommonUtils.<AnnotationMirror>parseAnnotationValue(this.elements, trigger.triggerAnnotation(), "value");
            var triggerParameter = ParameterSpec.builder(triggerClassName, "trigger").addAnnotation(AnnotationSpec.get(tag)).build();
            component.addParameter(triggerParameter);
        } else if (this.types.isSameType(trigger.triggerAnnotation().getAnnotationType(), this.scheduleWithCronTypeMirror)) {
            var identity = Optional.ofNullable(CommonUtils.<String>parseAnnotationValue(elements, trigger.triggerAnnotation(), "identity"))
                .filter(Predicate.not(String::isBlank))
                .orElse(type.getQualifiedName() + "#" + method.getSimpleName());
            var cron = CommonUtils.<String>parseAnnotationValue(elements, trigger.triggerAnnotation(), "value");
            var cronSchedule = CodeBlock.of("$S", cron);
            var configPath = CommonUtils.<String>parseAnnotationValue(elements, trigger.triggerAnnotation(), "config");
            if (configPath != null && !configPath.isBlank()) {
                var configClassName = this.generateCronConfigRecord(type, method, cron);
                var b = MethodSpec.methodBuilder(configClassName.simpleName())
                    .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                    .returns(configClassName)
                    .addParameter(ClassName.get("com.typesafe.config", "Config"), "config");
                if (cron != null && !cron.isBlank()) {
                    b.addCode("if (!config.hasPath($S)) {\n  return new $T($S);\n}\n", configPath, configClassName, cron);
                }
                module.addMethod(b.addCode("""
                        var value = config.getValue($S);
                        var cron = switch (value.valueType()) {
                            case STRING -> value.unwrapped().toString();
                            default -> throw ru.tinkoff.kora.config.common.extractor.ConfigValueExtractionException.unexpectedValueType(value, com.typesafe.config.ConfigValueType.STRING);
                        };
                        """.stripIndent(), configPath)
                    .addCode("return new $T(cron);\n", configClassName)
                    .build());
                component.addParameter(configClassName, "config");
                cronSchedule = CodeBlock.of("config.cron()");
            } else {
                if (cron == null || cron.isBlank()) {
                    throw new ProcessingErrorException("Either value() or config() annotation parameter must be provided", method, trigger.triggerAnnotation());
                }
            }
            component.addCode("""
                var trigger = $T.newTrigger()
                  .withIdentity($S)
                  .withSchedule($T.cronSchedule($L))
                  .build();
                """.stripIndent(), triggerBuilderClassName, identity, cronScheduleBuilderClassName, cronSchedule.toString());
        } else {
            // never gonna happen
            throw new IllegalStateException();
        }
        component.addCode("var telemetry = telemetryFactory.get($T.class, $S);\n", typeMirror, method.getSimpleName().toString());

        component
            .addCode("return new $T(telemetry, object, trigger);\n", jobClassName);

        module.addMethod(component.build());
    }

    private ClassName generateCronConfigRecord(TypeElement type, ExecutableElement method, String defaultCron) throws IOException {
        var configRecordName = CommonUtils.getOuterClassesAsPrefix(method) + "CronConfig";
        var packageName = this.elements.getPackageOf(type).getQualifiedName().toString();

        var rb = new RecordClassBuilder(configRecordName)
            .addModifier(Modifier.PUBLIC);
        if (defaultCron != null && !defaultCron.isBlank()) {
            rb.addComponent("cron", TypeName.get(String.class), CodeBlock.of("$S", defaultCron));
        } else {
            rb.addComponent("cron", TypeName.get(String.class));
        }
        rb.writeTo(this.filer, packageName);

        return ClassName.get(packageName, configRecordName);
    }


    private ClassName generateJobClass(TypeElement type, ExecutableElement method) throws IOException {
        var className = CommonUtils.getOuterClassesAsPrefix(type) + type.getSimpleName() + "_" + method.getSimpleName() + "_Job";
        var packageName = this.elements.getPackageOf(type).getQualifiedName().toString();
        var typeMirror = type.asType();
        var callJob = method.getParameters().isEmpty()
            ? CodeBlock.of("ctx -> object.$L()", method.getSimpleName())
            : CodeBlock.of("object::$L", method.getSimpleName());

        var typeSpec = TypeSpec.classBuilder(className)
            .superclass(koraQuartzJobClassName)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addField(TypeName.get(typeMirror), "object", Modifier.PRIVATE, Modifier.FINAL)
            .addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(schedulingTelemetryClassName, "telemetry")
                .addParameter(TypeName.get(typeMirror), "object")
                .addParameter(triggerClassName, "trigger")
                .addCode("super(telemetry, $L, trigger);\n", callJob)
                .addCode("this.object = object;\n")
                .build())
            .build();

        var javaFile = JavaFile.builder(packageName, typeSpec).build();
        javaFile.writeTo(this.filer);

        return ClassName.get(packageName, className);
    }
}
