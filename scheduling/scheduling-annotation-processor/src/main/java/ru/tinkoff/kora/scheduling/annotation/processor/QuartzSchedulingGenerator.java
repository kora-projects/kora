package ru.tinkoff.kora.scheduling.annotation.processor;

import com.squareup.javapoet.*;
import ru.tinkoff.kora.annotation.processor.common.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public class QuartzSchedulingGenerator {
    public static ClassName scheduleWithTrigger = ClassName.get("ru.tinkoff.kora.scheduling.quartz", "ScheduleWithTrigger");
    public static ClassName scheduleWithCron = ClassName.get("ru.tinkoff.kora.scheduling.quartz", "ScheduleWithCron");

    private static final ClassName koraQuartzJobClassName = ClassName.get("ru.tinkoff.kora.scheduling.quartz", "KoraQuartzJob");
    private static final ClassName schedulingTelemetryClassName = ClassName.get("ru.tinkoff.kora.scheduling.common.telemetry", "SchedulingTelemetry");
    private static final ClassName schedulingTelemetryFactoryClassName = ClassName.get("ru.tinkoff.kora.scheduling.common.telemetry", "SchedulingTelemetryFactory");
    private static final ClassName triggerClassName = ClassName.get("org.quartz", "Trigger");
    private static final ClassName schedulerClassName = ClassName.get("org.quartz", "Scheduler");
    private static final ClassName triggerBuilderClassName = ClassName.get("org.quartz", "TriggerBuilder");
    private static final ClassName cronScheduleBuilderClassName = ClassName.get("org.quartz", "CronScheduleBuilder");
    private final Elements elements;
    private final ProcessingEnvironment processingEnv;

    public QuartzSchedulingGenerator(ProcessingEnvironment processingEnv) {
        this.elements = processingEnv.getElementUtils();
        this.processingEnv = processingEnv;
    }

    public void generate(TypeElement type, ExecutableElement method, TypeSpec.Builder module, SchedulingTrigger trigger) {
        var jobClassName = this.generateJobClass(type, method);
        var typeMirror = type.asType();

        var component = MethodSpec.methodBuilder(type.getSimpleName() + "_" + method.getSimpleName() + "_Job")
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .returns(jobClassName)
            .addParameter(schedulingTelemetryFactoryClassName, "telemetryFactory")
            .addParameter(TypeName.get(typeMirror), "object");


        var annotationType = ClassName.get((TypeElement) trigger.triggerAnnotation().getAnnotationType().asElement());
        if (annotationType.equals(scheduleWithTrigger)) {
            var tag = AnnotationUtils.<AnnotationMirror>parseAnnotationValue(this.elements, trigger.triggerAnnotation(), "value");
            var triggerParameter = ParameterSpec.builder(triggerClassName, "trigger").addAnnotation(AnnotationSpec.get(tag)).build();
            component.addParameter(triggerParameter);
            component.addCode("var telemetry = telemetryFactory.get(null, $T.class, $S);\n", typeMirror, method.getSimpleName().toString());
        } else if (annotationType.equals(scheduleWithCron)) {
            var identity = Optional.ofNullable(AnnotationUtils.<String>parseAnnotationValue(elements, trigger.triggerAnnotation(), "identity"))
                .filter(Predicate.not(String::isBlank))
                .orElse(type.getQualifiedName() + "#" + method.getSimpleName());
            var cron = AnnotationUtils.<String>parseAnnotationValue(elements, trigger.triggerAnnotation(), "value");
            var cronSchedule = CodeBlock.of("$S", cron);
            var configPath = AnnotationUtils.<String>parseAnnotationValue(elements, trigger.triggerAnnotation(), "config");
            if (configPath != null && !configPath.isBlank()) {
                var configClassName = this.generateCronConfigRecord(type, method, cron);
                var b = MethodSpec.methodBuilder(configClassName.simpleName())
                    .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                    .returns(configClassName)
                    .addParameter(CommonClassNames.config, "config")
                    .addParameter(ParameterizedTypeName.get(CommonClassNames.configValueExtractor, configClassName), "extractor");

                if (cron != null && !cron.isBlank()) {
                    b.addStatement("var value = config.get($S)", configPath);
                    b.beginControlFlow("if (value instanceof $T.NullValue)", CommonClassNames.configValue)
                        .addCode("return extractor.extract($>\n")
                        .addCode("new $T.ObjectValue(value.origin(), $T.of($S, new $T.StringValue(value.origin(), $S)))", CommonClassNames.configValue, Map.class, "cron", CommonClassNames.configValue, cron)
                        .addCode("$<\n);\n")
                        .endControlFlow();
                } else {
                    b.addStatement("var value = config.get($S)", configPath);
                }
                b.beginControlFlow("if (value instanceof $T.StringValue str)", CommonClassNames.configValue)
                    .addStatement("var cron = str.value()")
                    .addCode("return extractor.extract($>\n")
                    .addCode("new $T.ObjectValue(value.origin(), $T.of($S, new $T.StringValue(value.origin(), cron)))", CommonClassNames.configValue, Map.class, "cron", CommonClassNames.configValue)
                    .addCode("$<\n);\n")
                    .nextControlFlow("else if (value instanceof $T.ObjectValue obj)", CommonClassNames.configValue)
                    .addStatement("return extractor.extract(obj)")
                    .nextControlFlow("else")
                    .addStatement("throw ru.tinkoff.kora.config.common.extractor.ConfigValueExtractionException.unexpectedValueType(value, $T.StringValue.class)", CommonClassNames.configValue)
                    .endControlFlow();

                module.addMethod(b.build());
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
                  .startAt(new $T(0))
                  .build();
                """.stripIndent(), triggerBuilderClassName, identity, cronScheduleBuilderClassName, cronSchedule.toString(), Date.class);
            if (configPath != null && !configPath.isBlank()) {
                component.addCode("var telemetry = telemetryFactory.get(config.telemetry(), $T.class, $S);\n", typeMirror, method.getSimpleName().toString());
            } else {
                component.addCode("var telemetry = telemetryFactory.get(null, $T.class, $S);\n", typeMirror, method.getSimpleName().toString());
            }
        } else {
            // never gonna happen
            throw new IllegalStateException();
        }

        component
            .addCode("return new $T(telemetry, object, trigger);\n", jobClassName);

        module.addMethod(component.build());
    }

    private ClassName generateCronConfigRecord(TypeElement type, ExecutableElement method, String defaultCron) {
        var configRecordName = NameUtils.getOuterClassesAsPrefix(method) + "CronConfig";
        var packageName = this.elements.getPackageOf(type).getQualifiedName().toString();

        var config = TypeSpec.interfaceBuilder(configRecordName)
            .addOriginatingElement(method)
            .addAnnotation(AnnotationUtils.generated(JdkSchedulingGenerator.class))
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(CommonClassNames.configValueExtractorAnnotation)
            .addMethod(MethodSpec.methodBuilder("telemetry")
                .returns(CommonClassNames.telemetryConfig)
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .build()
            );

        if (defaultCron != null && !defaultCron.isBlank()) {
            config.addMethod(MethodSpec.methodBuilder("cron")
                .returns(String.class)
                .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                .addStatement("return $S", defaultCron)
                .build()
            );
        } else {
            config.addMethod(MethodSpec.methodBuilder("cron")
                .returns(String.class)
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .build()
            );
        }
        CommonUtils.safeWriteTo(this.processingEnv, JavaFile.builder(packageName, config.build()).build());

        return ClassName.get(packageName, configRecordName);
    }


    private ClassName generateJobClass(TypeElement type, ExecutableElement method) {
        var className = NameUtils.generatedType(type, method.getSimpleName() + "_Job");
        var packageName = this.elements.getPackageOf(type).getQualifiedName().toString();
        var typeMirror = type.asType();
        var callJob = method.getParameters().isEmpty()
            ? CodeBlock.of("ctx -> object.$L()", method.getSimpleName())
            : CodeBlock.of("object::$L", method.getSimpleName());

        var typeSpec = TypeSpec.classBuilder(className)
            .addOriginatingElement(method)
            .addAnnotation(AnnotationUtils.generated(QuartzSchedulingGenerator.class))
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
        CommonUtils.safeWriteTo(this.processingEnv, javaFile);

        return ClassName.get(packageName, className);
    }
}
