package ru.tinkoff.kora.kafka.annotation.processor.consumer;

import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.annotation.processor.common.TagUtils;
import ru.tinkoff.kora.kafka.annotation.processor.KafkaClassNames;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.util.Elements;
import java.util.Objects;
import java.util.Set;

import static ru.tinkoff.kora.kafka.annotation.processor.utils.KafkaUtils.*;

public class KafkaConsumerConfigGenerator {

    public KafkaConfigData generate(Elements elements, ExecutableElement method, AnnotationMirror listenerAnnotation) {
        var targetTags = findConsumerUserTags(method);
        TypeSpec tagBuilded;
        if (targetTags == null) {
            var tag = prepareConsumerTag(elements, method);
            targetTags = Set.of(tag);
            tagBuilded = TypeSpec.classBuilder(tag.simpleName())
                .addAnnotation(AnnotationUtils.generated(KafkaConsumerConfigGenerator.class))
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .build();
        } else {
            tagBuilded = null;
        }

        var configPath = AnnotationUtils.parseAnnotationValueWithoutDefault(listenerAnnotation, "value");
        var methodBuilder = MethodSpec.methodBuilder(prepareMethodName(method, "Config"))
            .returns(KafkaClassNames.kafkaConsumerConfig)
            .addParameter(CommonClassNames.config, "config")
            .addParameter(ParameterizedTypeName.get(CommonClassNames.configValueExtractor, KafkaClassNames.kafkaConsumerConfig), "extractor")
            .addStatement("var configValue = config.get($S)", configPath)
            .addStatement("return extractor.extract(configValue)")
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .addAnnotation(TagUtils.makeAnnotationSpecForTypes(targetTags));

        return new KafkaConfigData(tagBuilded, methodBuilder.build());
    }

    public record KafkaConfigData(@Nullable TypeSpec tag, MethodSpec configMethod) {
        public KafkaConfigData {
            Objects.requireNonNull(configMethod);
        }
    }
}
