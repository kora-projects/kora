package io.koraframework.scheduling.annotation.processor;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import io.koraframework.annotation.processor.common.AnnotationUtils;
import io.koraframework.annotation.processor.common.CommonClassNames;
import io.koraframework.annotation.processor.common.CommonUtils;
import io.koraframework.annotation.processor.common.NameUtils;
import io.koraframework.annotation.processor.common.ProcessingErrorException;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;

public final class DbSchedulingGenerator {

    public static final ClassName scheduleWithCron = ClassName.get("io.koraframework.scheduling.db.annotation", "ScheduleWithCron");
    public static final ClassName scheduleOnce = ClassName.get("io.koraframework.scheduling.db.annotation", "ScheduleOnce");
    public static final ClassName scheduleWithFixedDelay = ClassName.get("io.koraframework.scheduling.db.annotation", "ScheduleWithFixedDelay");

    private static final ClassName dbScheduledJobClassName = ClassName.get("io.koraframework.scheduling.db.scheduler.job", "DbSchedulerJob");
    private static final ClassName cronJobClassName = ClassName.get("io.koraframework.scheduling.db.job", "CronJob");
    private static final ClassName fixedDelayJobClassName = ClassName.get("io.koraframework.scheduling.db.job", "FixedDelayJob");
    private static final ClassName runOnceJobClassName = ClassName.get("io.koraframework.scheduling.db.job", "RunOnceJob");
    private static final ClassName schedulingJobConfigClassName = ClassName.get("io.koraframework.scheduling.common", "SchedulingJobConfig");
    private static final ClassName schedulingTelemetryFactoryClassName = ClassName.get("io.koraframework.scheduling.common.telemetry", "SchedulingTelemetryFactory");
    private static final ClassName nullableClassName = ClassName.get("org.jspecify.annotations", "Nullable");

    private final Elements elements;
    private final ProcessingEnvironment processingEnv;

    public DbSchedulingGenerator(ProcessingEnvironment processingEnv) {
        this.elements = processingEnv.getElementUtils();
        this.processingEnv = processingEnv;
    }

    public void generate(TypeElement type, Element method, TypeSpec.Builder module, SchedulingTrigger trigger) {
        var triggerTypeName = ClassName.get((TypeElement) trigger.triggerAnnotation().getAnnotationType().asElement());
        if (triggerTypeName.equals(scheduleWithCron)) {
            this.generateScheduleWithCron(type, method, module, trigger);
            return;
        }
        if (triggerTypeName.equals(scheduleWithFixedDelay)) {
            this.generateScheduleWithFixedDelay(type, method, module, trigger);
            return;
        }
        if (triggerTypeName.equals(scheduleOnce)) {
            this.generateScheduleOnce(type, method, module, trigger);
            return;
        }
        throw new IllegalStateException("Unknown trigger type: " + trigger.triggerAnnotation().getAnnotationType());
    }

    private void generateScheduleWithCron(TypeElement type, Element method, TypeSpec.Builder module, SchedulingTrigger trigger) {
        var configName = AnnotationUtils.<String>parseAnnotationValue(this.elements, trigger.triggerAnnotation(), "config");
        var cron = AnnotationUtils.<String>parseAnnotationValue(this.elements, trigger.triggerAnnotation(), "value");
        var name = name(type, method, AnnotationUtils.<String>parseAnnotationValue(this.elements, trigger.triggerAnnotation(), "name"));
        var component = component(type, method);

        if (configName.isEmpty()) {
            if (cron == null || cron.isBlank()) {
                throw new ProcessingErrorException("Either value() or config() annotation parameter must be provided", method, trigger.triggerAnnotation());
            }
            component
                .addCode("var telemetry = telemetryFactory.get(null, null, $T.class, $S);\n", type, method.getSimpleName())
                .addCode("return new $T(telemetry, () -> object.get().$N(), $S, $S);\n", cronJobClassName, method.getSimpleName(), name, cron);
        } else {
            var packageName = this.elements.getPackageOf(type).getQualifiedName().toString();
            var configClassName = NameUtils.generatedType(type, method.getSimpleName() + "_Config");
            var config = configType(type, method, configClassName)
                .addMethod(nullableStringMethod("name"))
                .addMethod(cron == null || cron.isBlank()
                    ? abstractStringMethod("cron")
                    : stringMethod("cron", cron));
            CommonUtils.safeWriteTo(this.processingEnv, JavaFile.builder(packageName, config.build()).build());
            module.addMethod(cronConfigComponent(packageName, configClassName, configName, cron));
            component.addParameter(ClassName.get(packageName, configClassName), "config");
            component
                .addCode("var telemetry = telemetryFactory.get($S, config.telemetry(), $T.class, $S);\n", configName, type, method.getSimpleName())
                .addCode("var name = config.name();\n")
                .addCode("if (name == null || name.isBlank()) name = $S;\n", name)
                .addCode("return new $T(telemetry, () -> object.get().$N(), name, config.cron());\n", cronJobClassName, method.getSimpleName());
        }
        module.addMethod(component.build());
    }

    private void generateScheduleWithFixedDelay(TypeElement type, Element method, TypeSpec.Builder module, SchedulingTrigger trigger) {
        var configName = AnnotationUtils.<String>parseAnnotationValue(this.elements, trigger.triggerAnnotation(), "config");
        var delay = AnnotationUtils.<Long>parseAnnotationValue(this.elements, trigger.triggerAnnotation(), "delay");
        var initialDelay = AnnotationUtils.<Long>parseAnnotationValue(this.elements, trigger.triggerAnnotation(), "initialDelay");
        var unit = AnnotationUtils.<VariableElement>parseAnnotationValue(this.elements, trigger.triggerAnnotation(), "unit");
        var name = name(type, method, AnnotationUtils.<String>parseAnnotationValue(this.elements, trigger.triggerAnnotation(), "name"));
        var component = component(type, method);

        if (configName.isEmpty()) {
            if (delay == null || delay == 0) {
                throw new ProcessingErrorException("Either delay() or config() annotation parameter must be provided", method, trigger.triggerAnnotation());
            }
            component
                .addCode("var telemetry = telemetryFactory.get(null, null, $T.class, $S);\n", type, method.getSimpleName())
                .addCode("var initialDelay = $T.of($L, $T.$L);\n", Duration.class, initialDelay, ChronoUnit.class, unit)
                .addCode("var delay = $T.of($L, $T.$L);\n", Duration.class, delay, ChronoUnit.class, unit)
                .addCode("return new $T(telemetry, () -> object.get().$N(), $S, initialDelay, delay);\n", fixedDelayJobClassName, method.getSimpleName(), name);
        } else {
            var packageName = this.elements.getPackageOf(type).getQualifiedName().toString();
            var configClassName = NameUtils.generatedType(type, method.getSimpleName() + "_Config");
            var config = configType(type, method, configClassName)
                .addMethod(nullableStringMethod("name"))
                .addMethod(delay == null || delay == 0
                    ? abstractDurationMethod("delay")
                    : durationMethod("delay", delay, unit))
                .addMethod(durationMethod("initialDelay", initialDelay, unit));
            CommonUtils.safeWriteTo(this.processingEnv, JavaFile.builder(packageName, config.build()).build());
            module.addMethod(configComponent(packageName, configClassName, configName));
            component.addParameter(ClassName.get(packageName, configClassName), "config");
            component
                .addCode("var telemetry = telemetryFactory.get($S, config.telemetry(), $T.class, $S);\n", configName, type, method.getSimpleName())
                .addCode("var name = config.name();\n")
                .addCode("if (name == null || name.isBlank()) name = $S;\n", name)
                .addCode("return new $T(telemetry, () -> object.get().$N(), name, config.initialDelay(), config.delay());\n", fixedDelayJobClassName, method.getSimpleName());
        }
        module.addMethod(component.build());
    }

    private void generateScheduleOnce(TypeElement type, Element method, TypeSpec.Builder module, SchedulingTrigger trigger) {
        var configName = AnnotationUtils.<String>parseAnnotationValue(this.elements, trigger.triggerAnnotation(), "config");
        var delay = AnnotationUtils.<Long>parseAnnotationValue(this.elements, trigger.triggerAnnotation(), "delay");
        var unit = AnnotationUtils.<VariableElement>parseAnnotationValue(this.elements, trigger.triggerAnnotation(), "unit");
        var name = name(type, method, AnnotationUtils.<String>parseAnnotationValue(this.elements, trigger.triggerAnnotation(), "name"));
        var component = component(type, method);

        if (configName.isEmpty()) {
            if (delay == null || delay == 0) {
                throw new ProcessingErrorException("Either delay() or config() annotation parameter must be provided", method, trigger.triggerAnnotation());
            }
            component
                .addCode("var telemetry = telemetryFactory.get(null, null, $T.class, $S);\n", type, method.getSimpleName())
                .addCode("var delay = $T.of($L, $T.$L);\n", Duration.class, delay, ChronoUnit.class, unit)
                .addCode("return new $T(telemetry, () -> object.get().$N(), $S, delay);\n", runOnceJobClassName, method.getSimpleName(), name);
        } else {
            var packageName = this.elements.getPackageOf(type).getQualifiedName().toString();
            var configClassName = NameUtils.generatedType(type, method.getSimpleName() + "_Config");
            var config = configType(type, method, configClassName)
                .addMethod(nullableStringMethod("name"))
                .addMethod(delay == null || delay == 0
                    ? abstractDurationMethod("delay")
                    : durationMethod("delay", delay, unit));
            CommonUtils.safeWriteTo(this.processingEnv, JavaFile.builder(packageName, config.build()).build());
            module.addMethod(configComponent(packageName, configClassName, configName));
            component.addParameter(ClassName.get(packageName, configClassName), "config");
            component
                .addCode("var telemetry = telemetryFactory.get($S, config.telemetry(), $T.class, $S);\n", configName, type, method.getSimpleName())
                .addCode("var name = config.name();\n")
                .addCode("if (name == null || name.isBlank()) name = $S;\n", name)
                .addCode("return new $T(telemetry, () -> object.get().$N(), name, config.delay());\n", runOnceJobClassName, method.getSimpleName());
        }
        module.addMethod(component.build());
    }

    private MethodSpec.Builder component(TypeElement type, Element method) {
        return MethodSpec.methodBuilder(NameUtils.generatedType(type, method.getSimpleName() + "_Job"))
            .addModifiers(Modifier.DEFAULT, Modifier.PUBLIC)
            .addParameter(schedulingTelemetryFactoryClassName, "telemetryFactory")
            .addParameter(ParameterizedTypeName.get(CommonClassNames.valueOf, TypeName.get(type.asType())), "object")
            .returns(dbScheduledJobClassName)
            .addAnnotation(CommonClassNames.root);
    }

    private TypeSpec.Builder configType(TypeElement type, Element method, String configClassName) {
        return TypeSpec.interfaceBuilder(configClassName)
            .addOriginatingElement(method)
            .addAnnotation(AnnotationUtils.generated(DbSchedulingGenerator.class))
            .addModifiers(Modifier.PUBLIC)
            .addSuperinterface(schedulingJobConfigClassName)
            .addAnnotation(CommonClassNames.configMapperAnnotation);
    }

    private static String name(TypeElement type, Element method, String name) {
        return name == null || name.isBlank()
            ? type.getSimpleName() + "#" + method.getSimpleName()
            : name;
    }

    private static MethodSpec stringMethod(String name, String value) {
        return MethodSpec.methodBuilder(name)
            .returns(String.class)
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .addStatement("return $S", value)
            .build();
    }

    private static MethodSpec abstractStringMethod(String name) {
        return MethodSpec.methodBuilder(name)
            .returns(String.class)
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .build();
    }

    private static MethodSpec nullableStringMethod(String name) {
        return MethodSpec.methodBuilder(name)
            .returns(String.class)
            .addAnnotation(nullableClassName)
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .build();
    }

    private static MethodSpec durationMethod(String name, long value, VariableElement unit) {
        return MethodSpec.methodBuilder(name)
            .returns(Duration.class)
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .addStatement("return $T.of($L, $T.$L)", Duration.class, value, ChronoUnit.class, unit)
            .build();
    }

    private static MethodSpec abstractDurationMethod(String name) {
        return MethodSpec.methodBuilder(name)
            .returns(Duration.class)
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .build();
    }

    private static MethodSpec configComponent(String packageName, String configClassName, String configPath) {
        return MethodSpec.methodBuilder(configClassName)
            .addModifiers(Modifier.DEFAULT, Modifier.PUBLIC)
            .addParameter(CommonClassNames.config, "config")
            .addParameter(ParameterizedTypeName.get(CommonClassNames.configValueMapper, ClassName.get(packageName, configClassName)), "extractor")
            .addCode("var configValue = config.get($S);\n", configPath)
            .addStatement("return extractor.mapOrThrow(configValue)")
            .returns(ClassName.get(packageName, configClassName))
            .build();
    }

    private static MethodSpec cronConfigComponent(String packageName, String configClassName, String configPath, String defaultCron) {
        var configType = ClassName.get(packageName, configClassName);
        var method = MethodSpec.methodBuilder(configClassName)
            .addModifiers(Modifier.DEFAULT, Modifier.PUBLIC)
            .addParameter(CommonClassNames.config, "config")
            .addParameter(ParameterizedTypeName.get(CommonClassNames.configValueMapper, configType), "extractor")
            .returns(configType)
            .addStatement("var value = config.get($S)", configPath);

        if (defaultCron != null && !defaultCron.isBlank()) {
            method.beginControlFlow("if (value instanceof $T.NullValue)", CommonClassNames.configValue)
                .addCode("return extractor.mapOrThrow($>\n")
                .addCode("new $T.ObjectValue(value.origin(), $T.of($S, new $T.StringValue(value.origin(), $S)))", CommonClassNames.configValue, Map.class, "cron", CommonClassNames.configValue, defaultCron)
                .addCode("$<\n);\n")
                .endControlFlow();
        }
        method.beginControlFlow("if (value instanceof $T.StringValue str)", CommonClassNames.configValue)
            .addStatement("var cron = str.value()")
            .addCode("return extractor.mapOrThrow($>\n")
            .addCode("new $T.ObjectValue(value.origin(), $T.of($S, new $T.StringValue(value.origin(), cron)))", CommonClassNames.configValue, Map.class, "cron", CommonClassNames.configValue)
            .addCode("$<\n);\n")
            .nextControlFlow("else if (value instanceof $T.ObjectValue obj)", CommonClassNames.configValue)
            .addStatement("return extractor.mapOrThrow(obj)")
            .nextControlFlow("else")
            .addStatement("throw $T.unexpectedValueType(value, $T.StringValue.class)", CommonClassNames.configValueException, CommonClassNames.configValue)
            .endControlFlow();
        return method.build();
    }
}
