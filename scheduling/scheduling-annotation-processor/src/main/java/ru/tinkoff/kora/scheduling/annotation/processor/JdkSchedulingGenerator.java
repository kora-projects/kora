package ru.tinkoff.kora.scheduling.annotation.processor;

import com.palantir.javapoet.*;
import ru.tinkoff.kora.annotation.processor.common.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

public class JdkSchedulingGenerator {
    public static ClassName scheduleAtFixedRate = ClassName.get("ru.tinkoff.kora.scheduling.jdk.annotation", "ScheduleAtFixedRate");
    public static ClassName scheduleOnce = ClassName.get("ru.tinkoff.kora.scheduling.jdk.annotation", "ScheduleOnce");
    public static ClassName scheduleWithFixedDelay = ClassName.get("ru.tinkoff.kora.scheduling.jdk.annotation", "ScheduleWithFixedDelay");

    private static final ClassName fixedDelayJobClassName = ClassName.get("ru.tinkoff.kora.scheduling.jdk", "FixedDelayJob");
    private static final ClassName fixedRateJobClassName = ClassName.get("ru.tinkoff.kora.scheduling.jdk", "FixedRateJob");
    private static final ClassName runOnceJobClassName = ClassName.get("ru.tinkoff.kora.scheduling.jdk", "RunOnceJob");
    private static final ClassName schedulingTelemetryFactoryClassName = ClassName.get("ru.tinkoff.kora.scheduling.common.telemetry", "SchedulingTelemetryFactory");
    private static final ClassName jdkSchedulingExecutor = ClassName.get("ru.tinkoff.kora.scheduling.jdk", "JdkSchedulingExecutor");
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
        throw new IllegalStateException("Unknown trigger type: " + trigger.triggerAnnotation().getAnnotationType());
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
                .addCode("var telemetry = telemetryFactory.get(null, $T.class, $S);\n", type, method.getSimpleName())
                .addCode("var delay = $T.of($L, $T.$L);\n", Duration.class, delay, ChronoUnit.class, unit);
        } else {
            var config = TypeSpec.interfaceBuilder(configClassName)
                .addOriginatingElement(method)
                .addAnnotation(AnnotationUtils.generated(JdkSchedulingGenerator.class))
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(CommonClassNames.configValueExtractorAnnotation)
                .addMethod(MethodSpec.methodBuilder("telemetry")
                    .returns(CommonClassNames.telemetryConfig)
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .build()
                );
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
            componentMethod.addCode("var telemetry = telemetryFactory.get(config.telemetry(), $T.class, $S);\n", type, method.getSimpleName());
            componentMethod.addCode("var delay = config.delay();\n");
        }

        componentMethod.addCode("return new $T(telemetry, service, () -> object.get().$N(), delay);\n", runOnceJobClassName, method.getSimpleName());
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
                .addCode("var telemetry = telemetryFactory.get(null, $T.class, $S);\n", type, method.getSimpleName())
                .addCode("var initialDelay = $T.of($L, $T.$L);\n", Duration.class, initialDelay, ChronoUnit.class, unit)
                .addCode("var delay = $T.of($L, $T.$L);\n", Duration.class, delay, ChronoUnit.class, unit);
        } else {
            var config = TypeSpec.interfaceBuilder(configClassName)
                .addOriginatingElement(method)
                .addAnnotation(AnnotationUtils.generated(JdkSchedulingGenerator.class))
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(CommonClassNames.configValueExtractorAnnotation)
                .addMethod(MethodSpec.methodBuilder("telemetry")
                    .returns(CommonClassNames.telemetryConfig)
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .build()
                );
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
                .addCode("var telemetry = telemetryFactory.get(config.telemetry(), $T.class, $S);\n", type, method.getSimpleName())
                .addCode("var initialDelay = config.initialDelay();\n")
                .addCode("var delay = config.delay();\n");
        }
        componentMethod
            .addCode("return new $T(telemetry, service, () -> object.get().$N(), initialDelay, delay);\n", fixedDelayJobClassName, method.getSimpleName());
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
                .addCode("var telemetry = telemetryFactory.get(null, $T.class, $S);\n", type, method.getSimpleName())
                .addCode("var initialDelay = $T.of($L, $T.$L);\n", Duration.class, initialDelay, ChronoUnit.class, unit)
                .addCode("var period = $T.of($L, $T.$L);\n", Duration.class, period, ChronoUnit.class, unit);
        } else {
            var config = TypeSpec.interfaceBuilder(configClassName)
                .addOriginatingElement(method)
                .addAnnotation(AnnotationUtils.generated(JdkSchedulingGenerator.class))
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(CommonClassNames.configValueExtractorAnnotation)
                .addMethod(MethodSpec.methodBuilder("telemetry")
                    .returns(CommonClassNames.telemetryConfig)
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .build()
                );
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
                .addCode("var telemetry = telemetryFactory.get(config.telemetry(), $T.class, $S);\n", type, method.getSimpleName())
                .addCode("var initialDelay = config.initialDelay();\n")
                .addCode("var period = config.period();\n");
        }
        componentMethod
            .addCode("return new $T(telemetry, service, () -> object.get().$N(), initialDelay, period);\n", fixedRateJobClassName, method.getSimpleName());
        module.addMethod(componentMethod.build());
    }

    private static MethodSpec configComponent(String packageName, String configClassName, String configPath) {
        return MethodSpec.methodBuilder(configClassName)
            .addModifiers(Modifier.DEFAULT, Modifier.PUBLIC)
            .addParameter(CommonClassNames.config, "config")
            .addParameter(
                ParameterizedTypeName.get(
                    ClassName.get("ru.tinkoff.kora.config.common.extractor", "ConfigValueExtractor"),
                    ClassName.get(packageName, configClassName)
                ),
                "extractor"
            )
            .addCode("var configValue = config.get($S);\n", configPath)
            .addStatement("return $T.ofNullable(extractor.extract(configValue)).orElseThrow(() -> $T.missingValueAfterParse(configValue))", Optional.class, CommonClassNames.configValueExtractionException)
            .returns(ClassName.get(packageName, configClassName))
            .build();

    }
}
