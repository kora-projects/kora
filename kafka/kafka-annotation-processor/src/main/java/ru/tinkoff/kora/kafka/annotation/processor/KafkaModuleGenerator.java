package ru.tinkoff.kora.kafka.annotation.processor;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.common.Module;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.stream.Collectors;

public class KafkaModuleGenerator {
    private final ProcessingEnvironment processingEnv;
    private final Elements elements;
    private final Types types;
    private final KafkaConsumerGenerator kafkaConsumerGenerator;
    private final KafkaConfigGenerator kafkaConfigGenerator;

    public KafkaModuleGenerator(ProcessingEnvironment processingEnv, KafkaConsumerGenerator kafkaConsumerGenerator, KafkaConfigGenerator kafkaConfigGenerator) {
        this.processingEnv = processingEnv;
        this.elements = processingEnv.getElementUtils();
        this.types = processingEnv.getTypeUtils();
        this.kafkaConsumerGenerator = kafkaConsumerGenerator;
        this.kafkaConfigGenerator = kafkaConfigGenerator;
    }

    public final JavaFile generateModule(Element element) {
        var classBuilder = TypeSpec.interfaceBuilder(element.getSimpleName().toString() + "Module")
            .addOriginatingElement(element)
            .addModifiers(Modifier.PUBLIC);

        if (element.getAnnotation(Component.class) != null) {
            classBuilder.addAnnotation(AnnotationSpec.builder(Module.class).build());
        }

        var error = false;
        var methods = element.getEnclosedElements()
            .stream()
            .filter(e -> e.getKind() == ElementKind.METHOD)
            .map(ExecutableElement.class::cast)
            .collect(Collectors.toList());

        for (var method : methods) {
            var configTagData = this.kafkaConfigGenerator.generate(method);
            if (configTagData != null && configTagData.configMethod() != null) {
                classBuilder.addMethod(configTagData.configMethod());
            }
            if (configTagData != null && configTagData.tag() != null) {
                classBuilder.addType(configTagData.tag());
            }
            var generatedMethod = this.kafkaConsumerGenerator.generate(method);
            if (generatedMethod != null) {
                classBuilder.addMethod(generatedMethod);
            }
        }

        var packageName = this.elements.getPackageOf(element);
        return JavaFile.builder(packageName.getQualifiedName().toString(), classBuilder.build()).build();
    }
}
