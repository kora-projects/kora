package ru.tinkoff.kora.kafka.annotation.processor.consumer;

import com.squareup.javapoet.*;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.kafka.annotation.processor.KafkaClassNames;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import java.util.Objects;

import static ru.tinkoff.kora.kafka.annotation.processor.utils.KafkaUtils.prepareMethodName;
import static ru.tinkoff.kora.kafka.annotation.processor.utils.KafkaUtils.prepareTagName;

public class KafkaConsumerConfigGenerator {

    public KafkaConfigData generate(ExecutableElement executableElement, AnnotationMirror listenerAnnotation) {
        var tagName = prepareTagName(executableElement);
        var tagBuilder = TypeSpec.classBuilder(tagName).addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL);
        var configPath = AnnotationUtils.parseAnnotationValueWithoutDefault(listenerAnnotation, "value");
        var tagsBlock = CodeBlock.builder().add("{");
        tagsBlock.add("$L.class", tagName);
        tagsBlock.add("}");
        var methodBuilder = MethodSpec.methodBuilder(prepareMethodName(executableElement, "Config"))
            .returns(KafkaClassNames.kafkaConsumerConfig)
            .addParameter(CommonClassNames.config, "config")
            .addParameter(ParameterizedTypeName.get(CommonClassNames.configValueExtractor, KafkaClassNames.kafkaConsumerConfig), "extractor")
            .addStatement("var configValue = config.getValue($S)", configPath)
            .addStatement("return extractor.extract(configValue)")
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .addAnnotation(AnnotationSpec.builder(CommonClassNames.tag).addMember("value", tagsBlock.build()).build());


        return new KafkaConfigData(tagBuilder.build(), methodBuilder.build());
    }

    public record KafkaConfigData(TypeSpec tag, MethodSpec configMethod) {
        public KafkaConfigData {
            Objects.requireNonNull(tag);
            Objects.requireNonNull(configMethod);
        }
    }
}
