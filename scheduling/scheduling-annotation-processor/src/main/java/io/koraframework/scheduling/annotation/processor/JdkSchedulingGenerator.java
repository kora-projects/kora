package io.koraframework.scheduling.annotation.processor;

import com.palantir.javapoet.*;
import io.koraframework.annotation.processor.common.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

public class JdkSchedulingGenerator {
    public static ClassName scheduleAtFixedRate = ClassName.get("io.koraframework.scheduling.jdk.annotation", "ScheduleAtFixedRate");
    public static ClassName scheduleOnce = ClassName.get("io.koraframework.scheduling.jdk.annotation", "ScheduleOnce");
    public static ClassName scheduleWithFixedDelay = ClassName.get("io.koraframework.scheduling.jdk.annotation", "ScheduleWithFixedDelay");
    public static ClassName scheduleWithCron = ClassName.get("io.koraframework.scheduling.jdk.annotation", "ScheduleWithCron");

    private static final ClassName fixedDelayJobClassName = ClassName.get("io.koraframework.scheduling.jdk", "FixedDelayJob");
    private static final ClassName fixedRateJobClassName = ClassName.get("io.koraframework.scheduling.jdk", "FixedRateJob");
    private static final ClassName runOnceJobClassName = ClassName.get("io.koraframework.scheduling.jdk", "RunOnceJob");
    private static final ClassName cronJobClassName = ClassName.get("io.koraframework.scheduling.jdk", "CronJob");
    private static final ClassName cronExpressionClassName = ClassName.get("io.koraframework.scheduling.jdk", "CronExpression");
    private static final ClassName schedulingJobConfigClassName = ClassName.get("io.koraframework.scheduling.common", "SchedulingJobConfig");
    private static final ClassName jobTelemetryConfigClassName = ClassName.get("io.koraframework.scheduling.common", "SchedulingJobConfig", "JobTelemetryConfig");
    private static final ClassName schedulingTelemetryFactoryClassName = ClassName.get("io.koraframework.scheduling.common.telemetry", "SchedulingTelemetryFactory");
    private static final ClassName jdkSchedulingExecutor = ClassName.get("io.koraframework.scheduling.jdk", "SchedulingJdkExecutor");
    private final Elements elements;
    private final ProcessingEnvironment processingEnv;

    public JdkSchedulingGenerator(ProcessingEnvironment processingEnv) {
        this.elements = processingEnv.getElementUtils();
        this.processingEnv = processingEnv;
    }

    public void generate(TypeElement type, Element method, TypeSpec.Builder module, SchedulingTrigger trigger) {
        var triggerTypeName = ClassName.get((TypeElement) trigger.triggerAnnotation().getAnnotationType().asElement());
        if (triggerTypeName.equals(scheduleAtFixedRate)) {
            this.generateScheduleAtFixedRate(type, method, module, trigger);
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
        if (triggerTypeName.equals(scheduleWithCron)) {
            this.generateScheduleWithCron(type, method, module, trigger);
            return;
        }
        throw new IllegalStateException("Unknown trigger type: " + trigger.triggerAnnotation().getAnnotationType());
    }

    private void generateScheduleWithCron(TypeElement type, Element method, TypeSpec.Builder module, SchedulingTrigger trigger) {
        var packageName = this.elements.getPackageOf(type).getQualifiedName().toString();
        var configName = AnnotationUtils.<String>parseAnnotationValue(this.elements, trigger.triggerAnnotation(), "config");
        var configClassName = NameUtils.generatedType(type, method.getSimpleName() + "_Config");
        var jobMethodName = NameUtils.generatedType(type, method.getSimpleName() + "_Job");
        var cron = AnnotationUtils.<String>parseAnnotationValue(this.elements, trigger.triggerAnnotation(), "value");
        var componentMethod = MethodSpec.methodBuilder(jobMethodName)
            .addModifiers(Modifier.DEFAULT, Modifier.PUBLIC)
            .addParameter(schedulingTelemetryFactoryClassName, "telemetryFactory")
            .addParameter(jdkSchedulingExecutor, "service")
            .addParameter(ParameterizedTypeName.get(CommonClassNames.valueOf, TypeName.get(type.asType())), "object")
            .returns(cronJobClassName)
            .addAnnotation(CommonClassNames.root);

        if (configName == null || configName.isBlank()) {
            if (cron == null || cron.isBlank()) {
                throw new ProcessingErrorException("Either value() or config() annotation parameter must be provided", method, trigger.triggerAnnotation());
            }
            componentMethod
                .addStatement("var telemetry = telemetryFactory.get(null, null, $T.class, $S)", type, method.getSimpleName())
                .addStatement("var cron = $T.parse($S)", cronExpressionClassName, cron);
        } else {
            var config = TypeSpec.interfaceBuilder(configClassName)
                .addOriginatingElement(method)
                .addAnnotation(AnnotationUtils.generated(JdkSchedulingGenerator.class))
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(schedulingJobConfigClassName)
                .addAnnotation(CommonClassNames.configValueExtractorAnnotation);

            if (cron == null || cron.isBlank()) {
                config.addMethod(MethodSpec.methodBuilder("cron")
                    .returns(String.class)
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .build()
                );
            } else {
                config.addMethod(MethodSpec.methodBuilder("cron")
                    .returns(String.class)
                    .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                    .addStatement("return $S", cron)
                    .build()
                );
            }

            module.addMethod(cronConfigComponent(packageName, configClassName, configName, cron));
            CommonUtils.safeWriteTo(this.processingEnv, JavaFile.builder(packageName, config.build()).build());

            componentMethod.addParameter(ClassName.get(packageName, configClassName), "config");
            componentMethod.addStatement("var telemetry = telemetryFactory.get($S, config.telemetry(), $T.class, $S)", configName, type, method.getSimpleName());
            componentMethod.addStatement("var cron = $T.parse(config.cron())", cronExpressionClassName);
        }

        componentMethod.addStatement("return new $T(telemetry, service, () -> object.get().$N(), cron)", cronJobClassName, method.getSimpleName());
        module.addMethod(componentMethod.build());
    }

    private void generateScheduleOnce(TypeElement type, Element method, TypeSpec.Builder module, SchedulingTrigger trigger) {
        var packageName = this.elements.getPackageOf(type).getQualifiedName().toString();
        var configName = AnnotationUtils.<String>parseAnnotationValue(this.elements, trigger.triggerAnnotation(), "config");
        var configClassName = NameUtils.generatedType(type, method.getSimpleName() + "_Config");
        var jobMethodName = NameUtils.generatedType(type, method.getSimpleName() + "_Job");
        var delay = AnnotationUtils.<Long>parseAnnotationValue(this.elements, trigger.triggerAnnotation(), "delay");
        var unit = AnnotationUtils.<VariableElement>parseAnnotationValue(this.elements, trigger.triggerAnnotation(), "unit");
        var componentMethod = MethodSpec.methodBuilder(jobMethodName)
            .addModifiers(Modifier.DEFAULT, Modifier.PUBLIC)
            .addParameter(schedulingTelemetryFactoryClassName, "telemetryFactory")
            .addParameter(jdkSchedulingExecutor, "service")
            .addParameter(ParameterizedTypeName.get(CommonClassNames.valueOf, TypeName.get(type.asType())), "object")
            .returns(runOnceJobClassName)
            .addAnnotation(CommonClassNames.root);

        if (configName.isEmpty()) {
            if (delay == null || delay == 0) {
                throw new ProcessingErrorException("Either delay() or config() annotation parameter must be provided", method, trigger.triggerAnnotation());
            }
            componentMethod
                .addStatement("var telemetry = telemetryFactory.get($S, null, $T.class, $S)", type.getQualifiedName() + "#" + method.getSimpleName(), type, method.getSimpleName())
                .addStatement("var delay = $T.of($L, $T.$L)", Duration.class, delay, ChronoUnit.class, unit);
        } else {
            var config = TypeSpec.interfaceBuilder(configClassName)
                .addOriginatingElement(method)
                .addAnnotation(AnnotationUtils.generated(JdkSchedulingGenerator.class))
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(schedulingJobConfigClassName)
                .addAnnotation(CommonClassNames.configValueExtractorAnnotation);

            if (delay == null || delay == 0) {
                config.addMethod(MethodSpec.methodBuilder("delay")
                    .returns(Duration.class)
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .build()
                );
            } else {
                config.addMethod(MethodSpec.methodBuilder("delay")
                    .returns(Duration.class)
                    .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                    .addStatement("return $T.of($L, $T.$L)", Duration.class, delay, ChronoUnit.class, unit)
                    .build()
                );
            }
            module.addMethod(configComponent(packageName, configClassName, configName));
            CommonUtils.safeWriteTo(this.processingEnv, JavaFile.builder(packageName, config.build()).build());

            componentMethod.addParameter(ClassName.get(packageName, configClassName), "config");
            componentMethod.addStatement("var telemetry = telemetryFactory.get($S, config.telemetry(), $T.class, $S)", configName, type, method.getSimpleName());
            componentMethod.addStatement("var delay = config.delay()");
        }

        componentMethod.addStatement("return new $T(telemetry, service, () -> object.get().$N(), delay)", runOnceJobClassName, method.getSimpleName());
        module.addMethod(componentMethod.build());
    }

    private void generateScheduleWithFixedDelay(TypeElement type, Element method, TypeSpec.Builder module, SchedulingTrigger trigger) {
        var packageName = this.elements.getPackageOf(type).getQualifiedName().toString();
        var configName = AnnotationUtils.<String>parseAnnotationValue(this.elements, trigger.triggerAnnotation(), "config");
        var configClassName = NameUtils.generatedType(type, method.getSimpleName() + "_Config");
        var jobMethodName = NameUtils.generatedType(type, method.getSimpleName() + "_Job");
        var initialDelay = AnnotationUtils.<Long>parseAnnotationValue(this.elements, trigger.triggerAnnotation(), "initialDelay");
        var delay = AnnotationUtils.<Long>parseAnnotationValue(this.elements, trigger.triggerAnnotation(), "delay");
        var unit = AnnotationUtils.<VariableElement>parseAnnotationValue(this.elements, trigger.triggerAnnotation(), "unit");
        var componentMethod = MethodSpec.methodBuilder(jobMethodName)
            .addModifiers(Modifier.DEFAULT, Modifier.PUBLIC)
            .addParameter(schedulingTelemetryFactoryClassName, "telemetryFactory")
            .addParameter(jdkSchedulingExecutor, "service")
            .addParameter(ParameterizedTypeName.get(CommonClassNames.valueOf, TypeName.get(type.asType())), "object")
            .returns(fixedDelayJobClassName)
            .addAnnotation(CommonClassNames.root);

        if (configName.isEmpty()) {
            if (delay == null || delay == 0) {
                throw new ProcessingErrorException("Either delay() or config() annotation parameter must be provided", method, trigger.triggerAnnotation());
            }
            componentMethod
                .addStatement("var telemetry = telemetryFactory.get($S, null, $T.class, $S)", type.getQualifiedName() + "#" + method.getSimpleName(), type, method.getSimpleName())
                .addStatement("var initialDelay = $T.of($L, $T.$L)", Duration.class, initialDelay, ChronoUnit.class, unit)
                .addStatement("var delay = $T.of($L, $T.$L)", Duration.class, delay, ChronoUnit.class, unit);
        } else {
            var config = TypeSpec.interfaceBuilder(configClassName)
                .addOriginatingElement(method)
                .addAnnotation(AnnotationUtils.generated(JdkSchedulingGenerator.class))
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(schedulingJobConfigClassName)
                .addAnnotation(CommonClassNames.configValueExtractorAnnotation);

            componentMethod.addParameter(ClassName.get(packageName, configClassName), "config");
            if (delay == null || delay == 0) {
                config.addMethod(MethodSpec.methodBuilder("delay")
                    .returns(Duration.class)
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .build()
                );
            } else {
                config.addMethod(MethodSpec.methodBuilder("delay")
                    .returns(Duration.class)
                    .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                    .addStatement("return $T.of($L, $T.$L)", Duration.class, delay, ChronoUnit.class, unit)
                    .build()
                );
            }
            config.addMethod(MethodSpec.methodBuilder("initialDelay")
                .returns(Duration.class)
                .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                .addStatement("return $T.of($L, $T.$L)", Duration.class, initialDelay, ChronoUnit.class, unit)
                .build()
            );
            module.addMethod(configComponent(packageName, configClassName, configName));
            CommonUtils.safeWriteTo(this.processingEnv, JavaFile.builder(packageName, config.build()).build());

            componentMethod
                .addStatement("var telemetry = telemetryFactory.get($S, config.telemetry(), $T.class, $S)", configName, type, method.getSimpleName())
                .addStatement("var initialDelay = config.initialDelay()")
                .addStatement("var delay = config.delay()");
        }
        componentMethod
            .addStatement("return new $T(telemetry, service, () -> object.get().$N(), initialDelay, delay)", fixedDelayJobClassName, method.getSimpleName());
        module.addMethod(componentMethod.build());
    }

    private void generateScheduleAtFixedRate(TypeElement type, Element method, TypeSpec.Builder module, SchedulingTrigger trigger) {
        var packageName = this.elements.getPackageOf(type).getQualifiedName().toString();
        var configName = AnnotationUtils.<String>parseAnnotationValue(this.elements, trigger.triggerAnnotation(), "config");
        var configClassName = NameUtils.generatedType(type, method.getSimpleName() + "_Config");
        var jobMethodName = NameUtils.generatedType(type, method.getSimpleName() + "_Job");
        var initialDelay = AnnotationUtils.<Long>parseAnnotationValue(this.elements, trigger.triggerAnnotation(), "initialDelay");
        var period = AnnotationUtils.<Long>parseAnnotationValue(this.elements, trigger.triggerAnnotation(), "period");
        var unit = AnnotationUtils.<VariableElement>parseAnnotationValue(this.elements, trigger.triggerAnnotation(), "unit");
        var componentMethod = MethodSpec.methodBuilder(jobMethodName)
            .addModifiers(Modifier.DEFAULT, Modifier.PUBLIC)
            .addParameter(schedulingTelemetryFactoryClassName, "telemetryFactory")
            .addParameter(jdkSchedulingExecutor, "service")
            .addParameter(ParameterizedTypeName.get(CommonClassNames.valueOf, TypeName.get(type.asType())), "object")
            .returns(fixedRateJobClassName)
            .addAnnotation(CommonClassNames.root);

        if (configName.isEmpty()) {
            if (period == null || period == 0) {
                throw new ProcessingErrorException("Either period() or config() annotation parameter must be provided", method, trigger.triggerAnnotation());
            }
            componentMethod
                .addStatement("var telemetry = telemetryFactory.get($S, null, $T.class, $S)", type.getQualifiedName() + "#" + method.getSimpleName(), type, method.getSimpleName())
                .addStatement("var initialDelay = $T.of($L, $T.$L)", Duration.class, initialDelay, ChronoUnit.class, unit)
                .addStatement("var period = $T.of($L, $T.$L)", Duration.class, period, ChronoUnit.class, unit);
        } else {
            var config = TypeSpec.interfaceBuilder(configClassName)
                .addOriginatingElement(method)
                .addAnnotation(AnnotationUtils.generated(JdkSchedulingGenerator.class))
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(schedulingJobConfigClassName)
                .addAnnotation(CommonClassNames.configValueExtractorAnnotation);
            if (period == null || period == 0) {
                config.addMethod(MethodSpec.methodBuilder("period")
                    .returns(Duration.class)
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .build()
                );
            } else {
                config.addMethod(MethodSpec.methodBuilder("period")
                    .returns(Duration.class)
                    .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                    .addStatement("return $T.of($L, $T.$L)", Duration.class, period, ChronoUnit.class, unit)
                    .build()
                );
            }
            config.addMethod(MethodSpec.methodBuilder("initialDelay")
                .returns(Duration.class)
                .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                .addStatement("return $T.of($L, $T.$L)", Duration.class, initialDelay, ChronoUnit.class, unit)
                .build()
            );
            module.addMethod(configComponent(packageName, configClassName, configName));
            CommonUtils.safeWriteTo(this.processingEnv, JavaFile.builder(packageName, config.build()).build());

            componentMethod.addParameter(ClassName.get(packageName, configClassName), "config");
            componentMethod
                .addStatement("var telemetry = telemetryFactory.get($S, config.telemetry(), $T.class, $S)", configName, type, method.getSimpleName())
                .addStatement("var initialDelay = config.initialDelay()")
                .addStatement("var period = config.period()");
        }
        componentMethod
            .addStatement("return new $T(telemetry, service, () -> object.get().$N(), initialDelay, period)", fixedRateJobClassName, method.getSimpleName());
        module.addMethod(componentMethod.build());
    }

    private static MethodSpec configComponent(String packageName, String configClassName, String configPath) {
        return MethodSpec.methodBuilder(configClassName)
            .addModifiers(Modifier.DEFAULT, Modifier.PUBLIC)
            .addParameter(CommonClassNames.config, "config")
            .addParameter(
                ParameterizedTypeName.get(
                    ClassName.get("io.koraframework.config.common.extractor", "ConfigValueExtractor"),
                    ClassName.get(packageName, configClassName)
                ),
                "extractor"
            )
            .addStatement("return extractor.extractOrThrow(config.get($S))", configPath)
            .returns(ClassName.get(packageName, configClassName))
            .build();

    }

    private static MethodSpec cronConfigComponent(String packageName, String configClassName, String configPath, String defaultCron) {
        var configType = ClassName.get(packageName, configClassName);
        var method = MethodSpec.methodBuilder(configClassName)
            .addModifiers(Modifier.DEFAULT, Modifier.PUBLIC)
            .addParameter(CommonClassNames.config, "config")
            .addParameter(ParameterizedTypeName.get(CommonClassNames.configValueExtractor, configType), "extractor")
            .returns(configType)
            .addStatement("var value = config.get($S)", configPath);

        if (defaultCron != null && !defaultCron.isBlank()) {
            method.beginControlFlow("if (value instanceof $T.NullValue)", CommonClassNames.configValue)
                .addCode("return extractor.extractOrThrow($>\n")
                .addCode("new $T.ObjectValue(value.origin(), $T.of($S, new $T.StringValue(value.origin(), $S)))", CommonClassNames.configValue, Map.class, "cron", CommonClassNames.configValue, defaultCron)
                .addCode("$<\n);\n")
                .endControlFlow();
        }
        method.beginControlFlow("if (value instanceof $T.StringValue str)", CommonClassNames.configValue)
            .addStatement("var cron = str.value()")
            .addCode("return extractor.extractOrThrow($>\n")
            .addCode("new $T.ObjectValue(value.origin(), $T.of($S, new $T.StringValue(value.origin(), cron)))", CommonClassNames.configValue, Map.class, "cron", CommonClassNames.configValue)
            .addCode("$<\n);\n")
            .nextControlFlow("else if (value instanceof $T.ObjectValue obj)", CommonClassNames.configValue)
            .addStatement("return extractor.extractOrThrow(obj)")
            .nextControlFlow("else")
            .addStatement("throw $T.unexpectedValueType(value, $T.StringValue.class)", CommonClassNames.configValueExtractionException, CommonClassNames.configValue)
            .endControlFlow();
        return method.build();
    }
}
