package ru.tinkoff.kora.scheduling.annotation.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import ru.tinkoff.kora.annotation.processor.common.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class KoraSchedulingAnnotationProcessor extends AbstractKoraProcessor {
    private static final Map<SchedulerType, List<ClassName>> triggerTypes = Map.of(
        SchedulerType.JDK, List.of(
            JdkSchedulingGenerator.scheduleOnce,
            JdkSchedulingGenerator.scheduleAtFixedRate,
            JdkSchedulingGenerator.scheduleWithFixedDelay
        ),
        SchedulerType.QUARTZ, List.of(
            QuartzSchedulingGenerator.scheduleWithCron,
            QuartzSchedulingGenerator.scheduleWithTrigger
        )
    );
    private static final Set<ClassName> triggers = Set.of(
        JdkSchedulingGenerator.scheduleOnce,
        JdkSchedulingGenerator.scheduleAtFixedRate,
        JdkSchedulingGenerator.scheduleWithFixedDelay,
        QuartzSchedulingGenerator.scheduleWithCron,
        QuartzSchedulingGenerator.scheduleWithTrigger
    );
    private JdkSchedulingGenerator jdkGenerator;
    private QuartzSchedulingGenerator quartzGenerator;

    @Override
    public Set<ClassName> getSupportedAnnotationClassNames() {
        return triggers;
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.jdkGenerator = new JdkSchedulingGenerator(processingEnv);
        this.quartzGenerator = new QuartzSchedulingGenerator(processingEnv);
    }

    @Override
    public void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv, Map<ClassName, List<AnnotatedElement>> annotatedElements) {
        var triggers = annotations.stream()
            .filter(te -> {
                for (var trigger : KoraSchedulingAnnotationProcessor.triggers) {
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
            var type = (TypeElement) entry.getValue().get(0).getEnclosingElement();
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
            .addAnnotation(AnnotationUtils.generated(KoraSchedulingAnnotationProcessor.class))
            .addAnnotation(CommonClassNames.module)
            .addModifiers(Modifier.PUBLIC);
        for (var method : methods) {
            var m = (ExecutableElement) method;
            var trigger = this.parseSchedulerType(method);
            switch (trigger.schedulerType()) {
                case JDK -> this.jdkGenerator.generate(type, method, module, trigger);
                case QUARTZ -> this.quartzGenerator.generate(type, m, module, trigger);
            }
        }
        var packageName = elements.getPackageOf(type).getQualifiedName().toString();
        var moduleFile = JavaFile.builder(packageName, module.build());
        CommonUtils.safeWriteTo(this.processingEnv, moduleFile.build());
    }

    private SchedulingTrigger parseSchedulerType(Element method) {
        for (var entry : KoraSchedulingAnnotationProcessor.triggerTypes.entrySet()) {
            var schedulerType = entry.getKey();
            for (var annotationType : entry.getValue()) {
                var annotation = AnnotationUtils.findAnnotation(this.elements, method, annotationType);
                if (annotation != null) {
                    return new SchedulingTrigger(schedulerType, annotation);
                }
            }
        }
        throw new IllegalStateException();
    }
}
