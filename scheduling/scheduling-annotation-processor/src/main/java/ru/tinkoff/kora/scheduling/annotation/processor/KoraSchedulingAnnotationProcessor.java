package ru.tinkoff.kora.scheduling.annotation.processor;

import com.palantir.javapoet.*;
import ru.tinkoff.kora.annotation.processor.common.AbstractKoraProcessor;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.common.annotation.Generated;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
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
    private static final List<ClassName> triggers = List.of(
        JdkSchedulingGenerator.scheduleOnce,
        JdkSchedulingGenerator.scheduleAtFixedRate,
        JdkSchedulingGenerator.scheduleWithFixedDelay,
        QuartzSchedulingGenerator.scheduleWithCron,
        QuartzSchedulingGenerator.scheduleWithTrigger
    );
    private JdkSchedulingGenerator jdkGenerator;
    private QuartzSchedulingGenerator quartzGenerator;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return triggers.stream()
            .map(ClassName::canonicalName)
            .collect(Collectors.toSet());
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.jdkGenerator = new JdkSchedulingGenerator(processingEnv);
        this.quartzGenerator = new QuartzSchedulingGenerator(processingEnv);
    }

    private static void ifElementExists(Elements elements, String name, Consumer<TypeElement> consumer) {
        var element = elements.getTypeElement(name);
        if (element == null) {
            return;
        }
        consumer.accept(element);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
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
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            // todo exceptions
        }

        return false;
    }

    private void generateModule(TypeElement type, List<? extends Element> methods) throws IOException {
        var module = TypeSpec.interfaceBuilder("$" + type.getSimpleName() + "_SchedulingModule")
            .addAnnotation(AnnotationSpec.builder(Generated.class)
                .addMember("value", CodeBlock.of("$S", KoraSchedulingAnnotationProcessor.class.getCanonicalName()))
                .build())
            .addAnnotation(AnnotationSpec.builder(ClassName.get("ru.tinkoff.kora.common", "Module")).build())
            .addModifiers(Modifier.PUBLIC)
            .addOriginatingElement(type);
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
        moduleFile.build().writeTo(this.processingEnv.getFiler());
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
