package ru.tinkoff.kora.kafka.annotation.processor;

import com.squareup.javapoet.*;
import com.typesafe.config.Config;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;

import javax.annotation.Nullable;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

import static ru.tinkoff.kora.kafka.annotation.processor.utils.KafkaUtils.prepareMethodName;
import static ru.tinkoff.kora.kafka.annotation.processor.utils.KafkaUtils.prepareTagName;

public class KafkaConfigGenerator {
    private final Elements elements;
    private final TypeElement configType;

    public KafkaConfigGenerator(ProcessingEnvironment processingEnv) {
        this.elements = processingEnv.getElementUtils();
        this.configType = this.elements.getTypeElement(KafkaClassNames.kafkaConsumerConfig.canonicalName());
    }

    @Nullable
    public KafkaConfigData generate(ExecutableElement executableElement) {
        var controller = executableElement.getEnclosingElement();
        var methodName = prepareMethodName(controller.getSimpleName().toString(), executableElement.getSimpleName().toString());
        var listenerAnnotation = AnnotationUtils.findAnnotation(executableElement, KafkaClassNames.kafkaIncoming);
        if (listenerAnnotation == null) {
            return null;
        }
        var tagName = prepareTagName(controller.getSimpleName().toString(), executableElement.getSimpleName().toString());
        var tagBuilder = TypeSpec.classBuilder(tagName).addModifiers(Modifier.PUBLIC, Modifier.STATIC);
        var configPath = AnnotationUtils.parseAnnotationValueWithoutDefault(listenerAnnotation, "value");
        var tagsBlock = CodeBlock.builder().add("{");
        tagsBlock.add("$L.class", tagName);
        tagsBlock.add("}");
        var methodBuilder = MethodSpec.methodBuilder(methodName + "Config")
            .returns(TypeName.get(configType.asType()))
            .addParameter(TypeName.get(Config.class), "config")
            .addParameter(ParameterizedTypeName.get(ClassName.get(ConfigValueExtractor.class), TypeName.get(configType.asType())), "extractor")
            .addStatement("var configValue = config.getValue($S)", configPath)
            .addStatement("return extractor.extract(configValue)")
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .addAnnotation(AnnotationSpec.builder(Tag.class).addMember("value", tagsBlock.build()).build());


        return new KafkaConfigData(tagBuilder.build(), methodBuilder.build());
    }

    public record KafkaConfigData(TypeSpec tag, MethodSpec configMethod) {}
}
