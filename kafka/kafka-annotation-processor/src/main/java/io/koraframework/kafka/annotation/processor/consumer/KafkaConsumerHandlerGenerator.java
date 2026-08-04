package io.koraframework.kafka.annotation.processor.consumer;

import com.palantir.javapoet.*;
import io.koraframework.annotation.processor.common.CommonClassNames;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import io.koraframework.annotation.processor.common.TagUtils;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.*;
import javax.lang.model.util.Elements;
import java.util.List;

import static io.koraframework.kafka.annotation.processor.KafkaClassNames.*;
import static io.koraframework.kafka.annotation.processor.utils.KafkaUtils.getConsumerTag;
import static io.koraframework.kafka.annotation.processor.utils.KafkaUtils.prepareMethodName;

public class KafkaConsumerHandlerGenerator {

    public HandlerMethod generate(Elements elements, ExecutableElement executableElement, List<ConsumerParameter> parameters) {
        var controller = (TypeElement) executableElement.getEnclosingElement();
        var methodName = prepareMethodName(executableElement, "Handler");
        var consumerTags = getConsumerTag(elements, executableElement);
        var tagAnnotation = TagUtils.makeAnnotationSpec(consumerTags);

        var delegateParamBuilder = ParameterSpec.builder(TypeName.get(controller.asType()), "controller");
        var delegateTag = TagUtils.parseTagValue(executableElement.getEnclosingElement());
        if (delegateTag != null) {
            delegateParamBuilder.addAnnotation(TagUtils.makeAnnotationSpec(delegateTag));
        }

        var methodBuilder = MethodSpec.methodBuilder(methodName)
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .addParameter(delegateParamBuilder.build())
            .addAnnotation(tagAnnotation)
            .returns(CommonClassNames.lifecycle);

        var hasRecords = parameters.stream().anyMatch(p -> p instanceof ConsumerParameter.Records);
        var hasRecord = parameters.stream().anyMatch(p -> p instanceof ConsumerParameter.Record);

        if (hasRecords) {
            return this.generateRecords(executableElement, parameters, methodBuilder);
        } else if (hasRecord) {
            return this.generateRecord(executableElement, parameters, methodBuilder);
        } else {
            return this.generateKeyValue(executableElement, parameters, methodBuilder);
        }
    }

    public record HandlerMethod(MethodSpec method, TypeName keyType, String keyTag, TypeName valueType, String valueTag) {}

    private HandlerMethod generateRecord(ExecutableElement executableElement, List<ConsumerParameter> parameters, MethodSpec.Builder methodBuilder) {
        var b = CodeBlock.builder();
        var recordParameter = parameters.stream().filter(p -> p instanceof ConsumerParameter.Record).map(ConsumerParameter.Record.class::cast).findFirst().orElseThrow();

        var recordType = (DeclaredType) recordParameter.element().asType();
        var keyTypeMirror = recordType.getTypeArguments().get(0);
        if (keyTypeMirror instanceof WildcardType || keyTypeMirror instanceof IntersectionType || keyTypeMirror instanceof UnionType) {
            if (!(keyTypeMirror instanceof WildcardType w && w.getSuperBound() == null && w.getExtendsBound() == null)) {
                throw new ProcessingErrorException(invalidRecordTypeError(executableElement, "key", keyTypeMirror.toString()), executableElement);
            }
        }
        var valueTypeMirror = recordType.getTypeArguments().get(1);
        if (valueTypeMirror instanceof WildcardType || valueTypeMirror instanceof IntersectionType || valueTypeMirror instanceof UnionType) {
            throw new ProcessingErrorException(invalidRecordTypeError(executableElement, "value", valueTypeMirror.toString()), executableElement);
        }
        var keyType = keyTypeMirror instanceof WildcardType ? ArrayTypeName.of(TypeName.BYTE) : TypeName.get(keyTypeMirror);
        var valueType = TypeName.get(valueTypeMirror);


        var catchesKeyException = parameters.stream().anyMatch(p -> p instanceof ConsumerParameter.KeyDeserializationException || p instanceof ConsumerParameter.Exception);
        var catchesValueException = parameters.stream().anyMatch(p -> p instanceof ConsumerParameter.ValueDeserializationException || p instanceof ConsumerParameter.Exception);

        methodBuilder.returns(ParameterizedTypeName.get(recordHandler, keyType, valueType));
        b.add("return (consumer, tctx, record) -> {$>\n");
        if (catchesKeyException || catchesValueException) {
            if (catchesKeyException) {
                b.add("$T keyException = null;\n", recordKeyDeserializationException);
            }
            if (catchesValueException) {
                b.add("$T valueException = null;\n", recordValueDeserializationException);
            }
            b.add("try {$>\n");
            if (catchesKeyException) {
                b.add("record.key();\n");
            }
            if (catchesValueException) {
                b.add("record.value();\n");
            }
            if (catchesKeyException) {
                b.add("$<\n} catch ($T e) {$>\n", recordKeyDeserializationException);
                b.add("keyException = e;");
            }
            if (catchesValueException) {
                b.add("$<\n} catch ($T e) {$>\n", recordValueDeserializationException);
                b.add("valueException = e;");
            }
            b.add("$<\n}\n");
        }

        b.add("controller.$N(", executableElement.getSimpleName());

        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) b.add(", ");
            var parameter = parameters.get(i);
            if (parameter instanceof ConsumerParameter.Consumer) {
                b.add("consumer");
            } else if (parameter instanceof ConsumerParameter.Record) {
                b.add("record");
            } else if (parameter instanceof ConsumerParameter.KeyDeserializationException) {
                b.add("keyException");
            } else if (parameter instanceof ConsumerParameter.ValueDeserializationException) {
                b.add("valueException");
            } else if (parameter instanceof ConsumerParameter.Exception) {
                b.add("keyException != null ? keyException : valueException");
            } else {
                throw new ProcessingErrorException(
                    unsupportedRecordParameterError(executableElement, parameter.element().asType().toString()),
                    parameter.element()
                );
            }
        }
        b.add(");");
        b.add("$<\n};\n");
        var keyTag = TagUtils.parseTagValue(keyTypeMirror);
        var valueTag = TagUtils.parseTagValue(valueTypeMirror);

        methodBuilder.addCode(b.build());
        return new HandlerMethod(methodBuilder.build(), keyType, keyTag, valueType, valueTag);
    }

    private HandlerMethod generateKeyValue(ExecutableElement executableElement, List<ConsumerParameter> parameters, MethodSpec.Builder methodBuilder) {
        var keyParameter = (ConsumerParameter.Unknown) null;
        var valueParameter = (ConsumerParameter.Unknown) null;
        var headersParameter = (ConsumerParameter.Headers) null;
        for (var parameter : parameters) {
            if (parameter instanceof ConsumerParameter.Unknown u) {
                if (valueParameter == null) {
                    valueParameter = u;
                } else if (keyParameter == null) {
                    keyParameter = valueParameter;
                    valueParameter = u;
                } else {
                    throw new ProcessingErrorException(tooManyPayloadParametersError(executableElement, parameter.element().getSimpleName().toString(), keyParameter.element().getSimpleName().toString(), valueParameter.element().getSimpleName().toString()), parameter.element());
                }
            } else if (parameter instanceof ConsumerParameter.Headers headers) {
                headersParameter = headers;
            }
        }
        if (valueParameter == null) {
            throw new ProcessingErrorException(noPayloadParameterError(executableElement), executableElement);
        }
        var keyTypeMirror = keyParameter == null ? null : keyParameter.element().asType();
        if (keyTypeMirror != null && !(keyTypeMirror instanceof DeclaredType || keyTypeMirror instanceof ArrayType || keyTypeMirror instanceof PrimitiveType)) {
            throw new ProcessingErrorException(invalidRecordTypeError(executableElement, "key", keyTypeMirror.toString()), executableElement);
        }
        var valueTypeMirror = valueParameter.element().asType();
        if (!(valueTypeMirror instanceof DeclaredType || valueTypeMirror instanceof ArrayType || valueTypeMirror instanceof PrimitiveType)) {
            throw new ProcessingErrorException(invalidRecordTypeError(executableElement, "value", valueTypeMirror.toString()), executableElement);
        }
        var keyType = keyTypeMirror == null || keyTypeMirror.toString().equals("java.lang.Object") ? ArrayTypeName.of(TypeName.BYTE) : TypeName.get(keyTypeMirror).box();
        var valueType = TypeName.get(valueTypeMirror).box();

        var catchesKeyException = keyParameter != null && parameters.stream().anyMatch(p -> p instanceof ConsumerParameter.KeyDeserializationException || p instanceof ConsumerParameter.Exception);
        var catchesValueException = parameters.stream().anyMatch(p -> p instanceof ConsumerParameter.ValueDeserializationException || p instanceof ConsumerParameter.Exception);

        methodBuilder.returns(ParameterizedTypeName.get(recordHandler, keyType, valueType));
        var b = CodeBlock.builder();
        b.add("return (consumer, tctx, record) -> {$>\n");
        if (catchesKeyException) {
            b.add("$T keyException = null;\n", recordKeyDeserializationException);
        }
        if (catchesValueException) {
            b.add("$T valueException = null;\n", recordValueDeserializationException);
        }
        if (keyParameter != null) {
            b.add("$T key = null;\n", keyType);
        }
        b.add("$T value = null;\n", valueType);
        if (headersParameter != null) {
            b.add("var headers = record.headers();\n");
        }
        if (catchesKeyException || catchesValueException) {
            b.add("try {$>\n");
        }
        if (keyParameter != null) {
            b.add("key = record.key();\n");
        }
        b.add("value = record.value();\n");
        if (catchesKeyException) {
            b.add("$<\n} catch ($T e) {$>\n", recordKeyDeserializationException);
            b.add("keyException = e;");
        }
        if (catchesValueException) {
            b.add("$<\n} catch ($T e) {$>\n", recordValueDeserializationException);
            b.add("valueException = e;");
        }
        if (catchesKeyException || catchesValueException) {
            b.add("$<\n}\n");
        }
        b.add("controller.$N(", executableElement.getSimpleName());

        var keySeen = false;
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) b.add(", ");
            var parameter = parameters.get(i);
            if (parameter instanceof ConsumerParameter.Consumer) {
                b.add("consumer");
            } else if (parameter instanceof ConsumerParameter.KeyDeserializationException) {
                b.add("keyException");
            } else if (parameter instanceof ConsumerParameter.ValueDeserializationException) {
                b.add("valueException");
            } else if (parameter instanceof ConsumerParameter.Exception) {
                if (keyParameter != null) {
                    b.add("keyException != null ? keyException : valueException");
                } else {
                    b.add("valueException");
                }
            } else if (parameter instanceof ConsumerParameter.Headers) {
                b.add("headers");
            } else if (parameter instanceof ConsumerParameter.Unknown) {
                if (keyParameter == null || keySeen) {
                    b.add("value");
                } else {
                    keySeen = true;
                    b.add("key");
                }
            } else {
                throw new ProcessingErrorException(
                    unsupportedRecordParameterError(executableElement, parameter.element().asType().toString()),
                    parameter.element()
                );
            }
        }
        b.add(");");
        b.add("$<\n};\n");
        var keyTag = keyParameter == null ? null : TagUtils.parseTagValue(keyParameter.element());
        var valueTag = TagUtils.parseTagValue(valueParameter.element());

        methodBuilder.addCode(b.build());
        return new HandlerMethod(methodBuilder.build(), keyType, keyTag, valueType, valueTag);
    }

    private HandlerMethod generateRecords(ExecutableElement executableElement, List<ConsumerParameter> parameters, MethodSpec.Builder methodBuilder) {
        var recordsParameter = parameters.stream().filter(r -> r instanceof ConsumerParameter.Records).map(ConsumerParameter.Records.class::cast).findFirst().orElseThrow();
        var keyTypeMirror = recordsParameter.key();
        var valueTypeMirror = recordsParameter.value();
        if (keyTypeMirror instanceof WildcardType || keyTypeMirror instanceof IntersectionType || keyTypeMirror instanceof UnionType) {
            if (!(keyTypeMirror instanceof WildcardType w && w.getSuperBound() == null && w.getExtendsBound() == null)) {
                throw new ProcessingErrorException(invalidRecordTypeError(executableElement, "key", keyTypeMirror.toString()), executableElement);
            }
        }
        if (valueTypeMirror instanceof WildcardType || valueTypeMirror instanceof IntersectionType || valueTypeMirror instanceof UnionType) {
            throw new ProcessingErrorException(invalidRecordTypeError(executableElement, "value", valueTypeMirror.toString()), executableElement);
        }

        var keyType = keyTypeMirror instanceof WildcardType w && w.getSuperBound() == null && w.getExtendsBound() == null ? ArrayTypeName.of(TypeName.BYTE) : TypeName.get(keyTypeMirror);
        var valueType = TypeName.get(valueTypeMirror);

        methodBuilder.returns(ParameterizedTypeName.get(recordsHandler, keyType, valueType));
        var b = CodeBlock.builder();
        b.add("return (consumer, tctx, records) -> {$>\n");
        b.add("controller.$N(", executableElement.getSimpleName());
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) {
                b.add(", ");
            }
            var parameter = parameters.get(i);
            if (parameter instanceof ConsumerParameter.Consumer) {
                b.add("consumer");
            } else if (parameter instanceof ConsumerParameter.RecordsTelemetry) {
                b.add("tctx");
            } else if (parameter instanceof ConsumerParameter.Records) {
                b.add("records");
            } else {
                throw new ProcessingErrorException(
                    unsupportedRecordsParameterError(executableElement, parameter.element().asType().toString()),
                    parameter.element()
                );
            }
        }
        b.add(");");
        b.add("$<\n};\n");
        var keyTag = TagUtils.parseTagValue(keyTypeMirror);
        var valueTag = TagUtils.parseTagValue(valueTypeMirror);

        methodBuilder.addCode(b.build());
        return new HandlerMethod(methodBuilder.build(), keyType, keyTag, valueType, valueTag);
    }

    private static String invalidRecordTypeError(ExecutableElement method, String part, String type) {
        return """
            Kafka listener method has invalid %s type:
              %s

            Problem:
              Kafka listener record %s type can't be represented by a concrete serializer/deserializer type.

            Hint:
              Use a concrete class, primitive, byte array, or a parameterized type with concrete type arguments.

            Fix:
              Change method %s %s type to a concrete supported type, or consume ConsumerRecord/ConsumerRecords with compatible type arguments.
            """.formatted(part, type, part, method.getSimpleName(), part);
    }

    private static String unsupportedRecordParameterError(ExecutableElement method, String type) {
        return """
            Kafka record listener method has unsupported parameter:
              %s

            Problem:
              Record listener parameters must be Kafka consumer, ConsumerRecord, record key, record value, deserialization exception, or telemetry parameters.

            Hint:
              Kora maps listener parameters by their type and position. Extra service-like parameters can't be read from a Kafka record.

            Fix:
              Remove the unsupported parameter from %s, or inject services into the listener class constructor instead.
            """.formatted(type, method.getSimpleName());
    }

    private static String unsupportedRecordsParameterError(ExecutableElement method, String type) {
        return """
            Kafka records listener method has unsupported parameter:
              %s

            Problem:
              Records listener parameters must be Kafka consumer, ConsumerRecords, or records telemetry parameters.

            Hint:
              Batch listeners receive the whole ConsumerRecords object instead of individual key/value parameters.

            Fix:
              Remove the unsupported parameter from %s, or switch to a single-record listener signature.
            """.formatted(type, method.getSimpleName());
    }

    private static String tooManyPayloadParametersError(ExecutableElement method, String extra, String key, String value) {
        return """
            Kafka listener method has too many payload parameters:
              %s

            Problem:
              Parameter '%s' can't be classified. Previous payload parameters were '%s' as key and '%s' as value.

            Hint:
              Key/value listener signatures support at most two unknown payload parameters: key first, value second.

            Fix:
              Remove the extra parameter, mark it as a supported Kafka parameter, or inject services into the listener class constructor.
            """.formatted(method.getSimpleName(), extra, key, value);
    }

    private static String noPayloadParameterError(ExecutableElement method) {
        return """
            Kafka listener method has no payload parameter:
              %s

            Problem:
              Listener method must declare ConsumerRecord, ConsumerRecords, or at least one non-service payload parameter.

            Hint:
              Kora needs to know the record value type to choose a deserializer and generated handler type.

            Fix:
              Add a value parameter, ConsumerRecord<K, V>, or ConsumerRecords<K, V> to the listener method.
            """.formatted(method.getSimpleName());
    }
}
