package ru.tinkoff.kora.kafka.annotation.processor.utils;

import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.annotation.processor.common.TagUtils;
import ru.tinkoff.kora.kafka.annotation.processor.KafkaClassNames;

import jakarta.annotation.Nullable;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import java.util.Set;

public final class KafkaPublisherUtils {

    private KafkaPublisherUtils() {}

    public record PublisherData(@Nullable TypeName keyType, Set<String> keyTag, TypeName valueType, Set<String> valueTag, VariableElement keyVar, VariableElement valueVar, VariableElement headersVar, VariableElement recordVar, VariableElement callback) {}

    public static PublisherData parsePublisherType(ExecutableElement method) {
        var key = (VariableElement) null;
        var value = (VariableElement) null;
        var headers = (VariableElement) null;
        var record = (VariableElement) null;
        var producerCallback = (VariableElement) null;
        for (var parameter : method.getParameters()) {
            if (KafkaUtils.isProducerCallback(parameter.asType())) {
                if (producerCallback != null) {
                    throw new ProcessingErrorException("Invalid publisher signature: only one Callback parameter is allowed", parameter);
                }
                producerCallback = parameter;
                continue;
            }
            if (KafkaUtils.isHeaders(parameter.asType())) {
                if (record != null) {
                    throw new ProcessingErrorException("Invalid publisher signature: Headers parameter can't be used with record parameter", parameter);
                }
                if (headers != null) {
                    throw new ProcessingErrorException("Invalid publisher signature: only one Headers parameter is allowed", parameter);
                }
                headers = parameter;
                continue;
            }
            if (KafkaUtils.isProducerRecord(parameter.asType())) {
                if (value != null || headers != null) {
                    throw new ProcessingErrorException("Invalid publisher signature: Record parameter can't be combined with other parameters", parameter);
                }
                if (AnnotationUtils.isAnnotationPresent(method, KafkaClassNames.kafkaTopicAnnotation)) {
                    throw new ProcessingErrorException("Invalid publisher signature: Record parameter can't be combined @Topic annotation", parameter);
                }
                record = parameter;
                continue;
            }
            if (record != null) {
                throw new ProcessingErrorException("Invalid publisher signature: Record parameter can't be combined with key or value parameters", parameter);
            }
            if (key != null) {
                throw new ProcessingErrorException("Invalid publisher signature: only ProducerRecord or Headers, key and value parameters are allowed", parameter);
            }
            if (value != null) {
                key = value;
            }
            value = parameter;
        }
        if (record != null) {
            var recordType = (DeclaredType) record.asType();
            var recordTypeName = (ParameterizedTypeName) TypeName.get(recordType).withoutAnnotations();
            var keyType = recordTypeName.typeArguments.get(0);
            var valueType = recordTypeName.typeArguments.get(1);
            var keyTag = TagUtils.parseTagValue(recordType.getTypeArguments().get(0));
            var valueTag = TagUtils.parseTagValue(recordType.getTypeArguments().get(1));
            return new PublisherData(keyType, keyTag, valueType, valueTag, key, value, headers, record, producerCallback);
        }
        if (!AnnotationUtils.isAnnotationPresent(method, KafkaClassNames.kafkaTopicAnnotation)) {
            throw new ProcessingErrorException("Invalid publisher signature: key/value/headers signature requires @Topic annotation", method);
        }
        assert value != null;
        var valueType = TypeName.get(value.asType()).withoutAnnotations();
        var valueTag = TagUtils.parseTagValue(value);
        if (key == null) {
            return new PublisherData(null, Set.of(), valueType, valueTag, key, value, headers, record, producerCallback);
        }
        var keyType = TypeName.get(key.asType()).withoutAnnotations();
        var keyTag = TagUtils.parseTagValue(key);
        return new PublisherData(keyType, keyTag, valueType, valueTag, key, value, headers, record, producerCallback);
    }
}
