package io.koraframework.kafka.annotation.processor.utils;

import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import org.jspecify.annotations.Nullable;
import io.koraframework.annotation.processor.common.AnnotationUtils;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import io.koraframework.annotation.processor.common.TagUtils;
import io.koraframework.kafka.annotation.processor.KafkaClassNames;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;

public final class KafkaPublisherUtils {

    private KafkaPublisherUtils() {}

    public record PublisherData(@Nullable TypeName keyType, String keyTag, TypeName valueType, String valueTag, VariableElement keyVar, VariableElement valueVar, VariableElement headersVar,
                                VariableElement recordVar, VariableElement callback) {}

    public static PublisherData parsePublisherType(ExecutableElement method) {
        var key = (VariableElement) null;
        var value = (VariableElement) null;
        var headers = (VariableElement) null;
        var record = (VariableElement) null;
        var producerCallback = (VariableElement) null;
        for (var parameter : method.getParameters()) {
            if (KafkaUtils.isProducerCallback(parameter.asType())) {
                if (producerCallback != null) {
                    throw new ProcessingErrorException(publisherSignatureError(method, "More than one Callback parameter was found.", "Keep only one Callback parameter."), parameter);
                }
                producerCallback = parameter;
                continue;
            }
            if (KafkaUtils.isHeaders(parameter.asType())) {
                if (record != null) {
                    throw new ProcessingErrorException(publisherSignatureError(method, "Headers parameter is used together with ProducerRecord parameter.", "Remove Headers parameter or stop using ProducerRecord and declare key/value/headers parameters separately."), parameter);
                }
                if (headers != null) {
                    throw new ProcessingErrorException(publisherSignatureError(method, "More than one Headers parameter was found.", "Keep only one Headers parameter."), parameter);
                }
                headers = parameter;
                continue;
            }
            if (KafkaUtils.isProducerRecord(parameter.asType())) {
                if (value != null || headers != null) {
                    throw new ProcessingErrorException(publisherSignatureError(method, "ProducerRecord parameter is combined with key, value, or headers parameters.", "Use either a single ProducerRecord parameter or separate key/value/headers parameters."), parameter);
                }
                if (AnnotationUtils.isAnnotationPresent(method, KafkaClassNames.kafkaTopicAnnotation)) {
                    throw new ProcessingErrorException(publisherSignatureError(method, "ProducerRecord parameter is combined with @Topic annotation.", "Remove @Topic because ProducerRecord already carries the topic, or replace ProducerRecord with key/value parameters."), parameter);
                }
                record = parameter;
                continue;
            }
            if (record != null) {
                throw new ProcessingErrorException(publisherSignatureError(method, "ProducerRecord parameter is combined with key or value parameter.", "Use either a single ProducerRecord parameter or separate key/value/headers parameters."), parameter);
            }
            if (key != null) {
                throw new ProcessingErrorException(publisherSignatureError(method, "Too many payload parameters were found.", "Use at most two payload parameters: key and value. Headers and Callback are allowed as special parameters."), parameter);
            }
            if (value != null) {
                key = value;
            }
            value = parameter;
        }
        if (record != null) {
            var recordType = (DeclaredType) record.asType();
            var recordTypeName = (ParameterizedTypeName) TypeName.get(recordType).withoutAnnotations();
            var keyType = recordTypeName.typeArguments().get(0);
            var valueType = recordTypeName.typeArguments().get(1);
            var keyTag = TagUtils.parseTagValue(recordType.getTypeArguments().get(0));
            var valueTag = TagUtils.parseTagValue(recordType.getTypeArguments().get(1));
            return new PublisherData(keyType, keyTag, valueType, valueTag, key, value, headers, record, producerCallback);
        }
        if (!AnnotationUtils.isAnnotationPresent(method, KafkaClassNames.kafkaTopicAnnotation)) {
            throw new ProcessingErrorException(publisherSignatureError(method, "Key/value/headers signature has no @Topic annotation.", "Add @Topic to the publisher method, or use ProducerRecord if the topic should come from the record."), method);
        }
        assert value != null;
        var valueType = TypeName.get(value.asType()).withoutAnnotations();
        var valueTag = TagUtils.parseTagValue(value);
        if (key == null) {
            return new PublisherData(null, null, valueType, valueTag, key, value, headers, record, producerCallback);
        }
        var keyType = TypeName.get(key.asType()).withoutAnnotations();
        var keyTag = TagUtils.parseTagValue(key);
        return new PublisherData(keyType, keyTag, valueType, valueTag, key, value, headers, record, producerCallback);
    }

    private static String publisherSignatureError(ExecutableElement method, String problem, String fix) {
        return """
            Kafka publisher method has invalid signature:
              %s

            Problem:
              %s

            Hint:
              Publisher methods support either a single ProducerRecord parameter, or a key/value style signature with optional Headers and Callback parameters.

            Fix:
              %s
            """.formatted(method, problem, fix);
    }
}
