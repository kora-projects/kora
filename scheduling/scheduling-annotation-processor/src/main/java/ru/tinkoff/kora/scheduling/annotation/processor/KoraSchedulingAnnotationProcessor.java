package ru.tinkoff.kora.scheduling.annotation.processor;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import ru.tinkoff.kora.annotation.processor.common.AbstractKoraProcessor;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class KoraSchedulingAnnotationProcessor extends AbstractKoraProcessor {
    private Map<SchedulerType, List<TypeMirror>> triggerTypes;
    private JdkSchedulingGenerator jdkGenerator;
    private TypeElement[] triggers;
    private QuartzSchedulingGenerator quartzGenerator;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Arrays.stream(triggers)
            .map(TypeElement::getQualifiedName)
            .map(Objects::toString)
            .collect(Collectors.toSet());
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.triggerTypes = new HashMap<>();
        var quartzTriggers = new ArrayList<TypeMirror>();
        var jdkTriggers = new ArrayList<TypeMirror>();
        var triggers = new ArrayList<TypeElement>();
        var jdkConsumer = (Consumer<TypeElement>) te -> {
            jdkTriggers.add(te.asType());
            triggers.add(te);
        };
        var quartzConsumer = (Consumer<TypeElement>) te -> {
            quartzTriggers.add(te.asType());
            triggers.add(te);
        };

        ifElementExists(elements, "ru.tinkoff.kora.scheduling.jdk.annotation.ScheduleAtFixedRate", jdkConsumer);
        ifElementExists(elements, "ru.tinkoff.kora.scheduling.jdk.annotation.ScheduleOnce", jdkConsumer);
        ifElementExists(elements, "ru.tinkoff.kora.scheduling.jdk.annotation.ScheduleWithFixedDelay", jdkConsumer);
        ifElementExists(elements, "ru.tinkoff.kora.scheduling.quartz.ScheduleWithTrigger", quartzConsumer);
        ifElementExists(elements, "ru.tinkoff.kora.scheduling.quartz.ScheduleWithCron", quartzConsumer);
        if (!jdkTriggers.isEmpty()) {
            this.jdkGenerator = new JdkSchedulingGenerator(processingEnv);
            this.triggerTypes.put(SchedulerType.JDK, jdkTriggers);
        }
        if (!quartzTriggers.isEmpty()) {
            this.quartzGenerator = new QuartzSchedulingGenerator(processingEnv);
            this.triggerTypes.put(SchedulerType.QUARTZ, quartzTriggers);
        }


        this.triggers = triggers.toArray(TypeElement[]::new);
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
        if (this.triggers.length == 0) {
            return false;
        }
        var scheduledMethods = roundEnv.getElementsAnnotatedWithAny(this.triggers);
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
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(AnnotationSpec.builder(ClassName.get("ru.tinkoff.kora.common", "Module")).build())
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
        for (var entry : this.triggerTypes.entrySet()) {
            var schedulerType = entry.getKey();
            for (var annotationType : entry.getValue()) {
                var annotation = CommonUtils.findAnnotation(this.elements, this.types, method, annotationType);
                if (annotation != null) {
                    return new SchedulingTrigger(schedulerType, annotation);
                }
            }
        }
        throw new IllegalStateException();
    }
}
