package ru.tinkoff.kora.kafka.annotation.processor.consumer;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.kafka.annotation.processor.KafkaClassNames;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

public class KafkaConsumerModuleGenerator {
    private final Elements elements;
    private final KafkaConsumerHandlerGenerator kafkaConsumerHandlerGenerator;
    private final KafkaConsumerConfigGenerator configGenerator;
    private final KafkaConsumerContainerGenerator kafkaConsumerContainerGenerator;

    public KafkaConsumerModuleGenerator(ProcessingEnvironment processingEnv, KafkaConsumerHandlerGenerator kafkaConsumerHandlerGenerator, KafkaConsumerConfigGenerator configGenerator, KafkaConsumerContainerGenerator kafkaConsumerContainerGenerator) {
        this.elements = processingEnv.getElementUtils();
        this.kafkaConsumerHandlerGenerator = kafkaConsumerHandlerGenerator;
        this.configGenerator = configGenerator;
        this.kafkaConsumerContainerGenerator = kafkaConsumerContainerGenerator;
    }

    public final JavaFile generateModule(TypeElement typeElement) {
        var classBuilder = TypeSpec.interfaceBuilder(typeElement.getSimpleName().toString() + "Module")
            .addOriginatingElement(typeElement)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(AnnotationSpec.builder(CommonClassNames.koraGenerated).addMember("value", CodeBlock.of("$S", KafkaConsumerModuleGenerator.class.getCanonicalName())).build())
            .addAnnotation(CommonClassNames.module);

        for (var element : typeElement.getEnclosedElements()) {
            if (element.getKind() != ElementKind.METHOD) {
                continue;
            }
            var method = (ExecutableElement) element;
            var annotation = AnnotationUtils.findAnnotation(method, KafkaClassNames.kafkaListener);
            if (annotation == null) {
                continue;
            }
            var configTagData = this.configGenerator.generate(method, annotation);
            classBuilder.addMethod(configTagData.configMethod());
            classBuilder.addType(configTagData.tag());

            var parameters = ConsumerParameter.parseParameters(method);
            var handler = this.kafkaConsumerHandlerGenerator.generate(method, parameters);
            classBuilder.addMethod(handler.method());

            var container = this.kafkaConsumerContainerGenerator.generate(method, handler, parameters);
            classBuilder.addMethod(container);
        }

        var packageName = this.elements.getPackageOf(typeElement);
        return JavaFile.builder(packageName.getQualifiedName().toString(), classBuilder.build()).build();
    }
}
