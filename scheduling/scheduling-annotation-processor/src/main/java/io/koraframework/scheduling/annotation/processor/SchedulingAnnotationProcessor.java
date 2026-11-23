package io.koraframework.scheduling.annotation.processor;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.ParameterSpec;
import com.palantir.javapoet.TypeSpec;
import io.koraframework.annotation.processor.common.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SchedulingAnnotationProcessor extends AbstractKoraProcessor {

    private static final Map<SchedulerType, List<ClassName>> TRIGGER_TYPES = Map.of(
        SchedulerType.JDK, List.of(
            JdkSchedulingGenerator.scheduleOnce,
            JdkSchedulingGenerator.scheduleAtFixedRate,
            JdkSchedulingGenerator.scheduleWithFixedDelay,
            JdkSchedulingGenerator.scheduleWithCron
        ),
        SchedulerType.QUARTZ, List.of(
            QuartzSchedulingGenerator.scheduleWithCron,
            QuartzSchedulingGenerator.scheduleWithTrigger
        ),
        SchedulerType.DB, List.of(
            DbSchedulingGenerator.scheduleWithCron,
            DbSchedulingGenerator.scheduleWithFixedDelay,
            DbSchedulingGenerator.scheduleOnce
        )
    );

    private static final Set<ClassName> TRIGGERS = Set.of(
        JdkSchedulingGenerator.scheduleOnce,
        JdkSchedulingGenerator.scheduleAtFixedRate,
        JdkSchedulingGenerator.scheduleWithFixedDelay,
        JdkSchedulingGenerator.scheduleWithCron,
        QuartzSchedulingGenerator.scheduleWithCron,
        QuartzSchedulingGenerator.scheduleWithTrigger,
        DbSchedulingGenerator.scheduleWithCron,
        DbSchedulingGenerator.scheduleWithFixedDelay,
        DbSchedulingGenerator.scheduleOnce);

    private static final ClassName schedulingModuleClassName = ClassName.get("io.koraframework.scheduling.common", "SchedulingModule");
    private static final ClassName SCHEDULING_UTILS = ClassName.get("io.koraframework.scheduling.common", "SchedulingUtils");

    /**
     * Optional time zone of CRON jobs: a {@code ZoneId} component tagged with {@code SchedulingModule}, the JVM default time zone when absent.
     * The job component of a {@code @Conditional} component exists under the same condition, otherwise it is scheduled
     * and fails on every execution, or fails the graph, when the condition is not met.
     */
    static List<AnnotationSpec> conditionalOf(TypeElement type) {
        var conditional = AnnotationUtils.findAnnotation(type, CommonClassNames.conditional);
        return conditional == null ? List.of() : List.of(AnnotationSpec.get(conditional));
    }

    static ParameterSpec zoneIdParameter() {
        return ParameterSpec.builder(ClassName.get(ZoneId.class), "zoneId")
            .addAnnotation(TagUtils.makeAnnotationSpec(schedulingModuleClassName))
            .addAnnotation(CommonClassNames.nullableAnnotation)
            .build();
    }

    /**
     * The scheduled method call as a lambda of a job interface that can't throw checked exceptions:
     * an exception declared by the method is rethrown unchanged, so the scheduler sees it as the method threw it
     * (a Quartz {@code JobExecutionException} keeps its {@code unscheduleFiringTrigger}/{@code refireImmediately} instructions).
     */
    static CodeBlock jobLambda(Element method, String parameters, CodeBlock call) {
        if (((ExecutableElement) method).getThrownTypes().isEmpty()) {
            return CodeBlock.of("$L -> $L", parameters, call);
        }
        return CodeBlock.builder()
            .add("$L -> {$>\n", parameters)
            .add("try {$>\n$L;$<\n", call)
            .add("} catch ($T e) {$>\nthrow $T.sneakyThrow(e);$<\n}\n", Throwable.class, SCHEDULING_UTILS)
            .add("$<}")
            .build();
    }

    /**
     * The {@code Runnable} of a JDK or database job that calls the scheduled method on the {@code ValueOf} named {@code object}.
     */
    static CodeBlock jobRunnable(Element method) {
        return jobLambda(method, "()", CodeBlock.of("object.get().$N()", method.getSimpleName()));
    }

    /**
     * The {@code unit} of a scheduling annotation: the generated job converts it with {@code Duration.of}, which rejects estimated units longer than a day.
     */
    static VariableElement durationUnit(Elements elements, Element method, AnnotationMirror annotation) {
        var unit = AnnotationUtils.<VariableElement>parseAnnotationValue(elements, annotation, "unit");
        var chronoUnit = ChronoUnit.valueOf(unit.getSimpleName().toString());
        if (chronoUnit.isDurationEstimated() && chronoUnit != ChronoUnit.DAYS) {
            throw new ProcessingErrorException("Unit ChronoUnit.%s has an estimated duration and can't be used for a scheduled job, use DAYS or a smaller unit".formatted(chronoUnit.name()), method, annotation);
        }
        return unit;
    }

    private JdkSchedulingGenerator jdkGenerator;
    private QuartzSchedulingGenerator quartzGenerator;
    private DbSchedulingGenerator dbGenerator;

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return TRIGGERS;
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.jdkGenerator = new JdkSchedulingGenerator(processingEnv);
        this.quartzGenerator = new QuartzSchedulingGenerator(processingEnv);
        this.dbGenerator = new DbSchedulingGenerator(processingEnv);
    }

    @Override
    public void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv, Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        var triggers = annotations.stream()
            .filter(te -> {
                for (var trigger : SchedulingAnnotationProcessor.TRIGGERS) {
                    if (te.getQualifiedName().contentEquals(trigger.canonicalName())) {
                        return true;
                    }
                }
                return false;
            })
            .toArray(TypeElement[]::new);
        var scheduledMethods = roundEnv.getElementsAnnotatedWithAny(triggers);
        var scheduledTypes = scheduledMethods.stream().collect(Collectors.groupingBy(e -> {
            var type = (TypeElement) e.getEnclosingElement();
            return type.getQualifiedName().toString();
        }));

        for (var entry : scheduledTypes.entrySet()) {
            var methods = entry.getValue();
            var type = (TypeElement) entry.getValue().getFirst().getEnclosingElement();
            try {
                this.generateModule(type, methods);
            } catch (ProcessingErrorException e) {
                e.printError(this.processingEnv);
            }
        }
    }

    private void generateModule(TypeElement type, List<? extends Element> methods) {
        var module = TypeSpec.interfaceBuilder("$" + type.getSimpleName() + "_SchedulingModule")
            .addOriginatingElement(type)
            .addAnnotation(AnnotationUtils.generated(SchedulingAnnotationProcessor.class))
            .addAnnotation(CommonClassNames.module)
            .addModifiers(Modifier.PUBLIC);
        for (var method : methods) {
            var m = (ExecutableElement) method;
            var trigger = this.parseSchedulerType(method);
            switch (trigger.schedulerType()) {
                case JDK -> this.jdkGenerator.generate(type, method, module, trigger);
                case QUARTZ -> this.quartzGenerator.generate(type, m, module, trigger);
                case DB -> this.dbGenerator.generate(type, method, module, trigger);
            }
        }
        var packageName = elements.getPackageOf(type).getQualifiedName().toString();
        var moduleFile = JavaFile.builder(packageName, module.build());
        CommonUtils.safeWriteTo(this.processingEnv, moduleFile.build());
    }

    private SchedulingTrigger parseSchedulerType(Element method) {
        for (var entry : SchedulingAnnotationProcessor.TRIGGER_TYPES.entrySet()) {
            var schedulerType = entry.getKey();
            for (var annotationType : entry.getValue()) {
                var annotation = AnnotationUtils.findAnnotation(this.elements, method, annotationType);
                if (annotation != null) {
                    return new SchedulingTrigger(schedulerType, annotation);
                }
            }
        }
        throw new IllegalStateException("""
            Kora internal error: scheduled method '%s' was selected for generation, but no supported scheduling annotation was found.

            Supported annotations: %s.
            """.formatted(method, SchedulingAnnotationProcessor.TRIGGERS).trim());
    }
}
