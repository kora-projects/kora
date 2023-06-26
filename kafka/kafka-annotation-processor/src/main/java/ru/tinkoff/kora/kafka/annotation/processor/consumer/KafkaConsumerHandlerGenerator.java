package ru.tinkoff.kora.kafka.annotation.processor.consumer;

import com.squareup.javapoet.*;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.annotation.processor.common.TagUtils;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.*;
import java.util.List;
import java.util.Set;

import static ru.tinkoff.kora.kafka.annotation.processor.KafkaClassNames.*;
import static ru.tinkoff.kora.kafka.annotation.processor.utils.KafkaUtils.prepareMethodName;
import static ru.tinkoff.kora.kafka.annotation.processor.utils.KafkaUtils.prepareTagName;

public class KafkaConsumerHandlerGenerator {

    public HandlerMethod generate(ExecutableElement executableElement, List<ConsumerParameter> parameters) {
        var controller = (TypeElement) executableElement.getEnclosingElement();
        var methodName = prepareMethodName(executableElement, "Handler");
        var tagName = prepareTagName(executableElement);
        var tagsBlock = CodeBlock.of("$L.class", tagName);
        var tag = AnnotationSpec.builder(CommonClassNames.tag).addMember("value", tagsBlock).build();
        var methodBuilder = MethodSpec.methodBuilder(methodName)
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .addParameter(TypeName.get(controller.asType()), "controller")
            .addAnnotation(tag)
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

    record HandlerMethod(MethodSpec method, TypeName keyType, Set<String> keyTag, TypeName valueType, Set<String> valueTag) {}

    private HandlerMethod generateRecord(ExecutableElement executableElement, List<ConsumerParameter> parameters, MethodSpec.Builder methodBuilder) {
        var b = CodeBlock.builder();
        var recordParameter = parameters.stream().filter(p -> p instanceof ConsumerParameter.Record).map(ConsumerParameter.Record.class::cast).findFirst().orElseThrow();

        var recordType = (DeclaredType) recordParameter.element().asType();
        var keyTypeMirror = recordType.getTypeArguments().get(0);
        if (keyTypeMirror instanceof WildcardType || keyTypeMirror instanceof IntersectionType || keyTypeMirror instanceof UnionType) {
            if (!(keyTypeMirror instanceof WildcardType w && w.getSuperBound() == null && w.getExtendsBound() == null)) {
                var message = "Kafka listener method has invalid key type %s".formatted(keyTypeMirror);
                throw new ProcessingErrorException(message, executableElement);
            }
        }
        var valueTypeMirror = recordType.getTypeArguments().get(1);
        if (valueTypeMirror instanceof WildcardType || valueTypeMirror instanceof IntersectionType || valueTypeMirror instanceof UnionType) {
            var message = "Kafka listener method has invalid value type %s".formatted(valueTypeMirror);
            throw new ProcessingErrorException(message, executableElement);
        }
        var keyType = keyTypeMirror instanceof WildcardType ? ArrayTypeName.of(TypeName.BYTE) : TypeName.get(keyTypeMirror);
        var valueType = TypeName.get(valueTypeMirror);


        var catchesKeyException = parameters.stream().anyMatch(p -> p instanceof ConsumerParameter.KeyDeserializationException || p instanceof ConsumerParameter.Exception);
        var catchesValueException = parameters.stream().anyMatch(p -> p instanceof ConsumerParameter.ValueDeserializationException || p instanceof ConsumerParameter.Exception);

        methodBuilder.returns(ParameterizedTypeName.get(recordHandler, keyType, valueType));
        b.add("return (consumer, tctx, record) -> {$>\n");
        if (catchesKeyException || catchesValueException) {
            if (catchesKeyException) b.add("$T keyException = null;\n", recordKeyDeserializationException);
            if (catchesValueException) b.add("$T valueException = null;\n", recordValueDeserializationException);
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
                    "Record listener can't have parameter of type %s, only consumer, record, record key, record value, exception and record telemetry are allowed".formatted(parameter.element().asType()),
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
        for (var parameter : parameters) {
            if (parameter instanceof ConsumerParameter.Unknown u) {
                if (valueParameter == null) {
                    valueParameter = u;
                } else if (keyParameter == null) {
                    keyParameter = valueParameter;
                    valueParameter = u;
                } else {
                    var message = "Kafka listener method has unknown type parameter '%s'. Previous unknown type parameters are: '%s'(detected as key), '%s'(detected as value)".formatted(
                        parameter.element().getSimpleName(), keyParameter.element().getSimpleName(), valueParameter.element().getSimpleName()
                    );
                    throw new ProcessingErrorException(message, parameter.element());
                }
            }
        }
        if (valueParameter == null) {
            var message = "Kafka listener method should have one of ConsumerRecord, ConsumerRecords or non service type parameters";
            throw new ProcessingErrorException(message, executableElement);
        }
        var keyTypeMirror = keyParameter == null ? null : keyParameter.element().asType();
        if (keyTypeMirror != null && !(keyTypeMirror instanceof DeclaredType || keyTypeMirror instanceof ArrayType || keyTypeMirror instanceof PrimitiveType)) {
            var message = "Kafka listener method has invalid key type %s".formatted(keyTypeMirror);
            throw new ProcessingErrorException(message, executableElement);
        }
        var valueTypeMirror = valueParameter.element().asType();
        if (!(valueTypeMirror instanceof DeclaredType || valueTypeMirror instanceof ArrayType || valueTypeMirror instanceof PrimitiveType)) {
            var message = "Kafka listener method has invalid value type %s".formatted(valueTypeMirror);
            throw new ProcessingErrorException(message, executableElement);
        }
        var keyType = keyTypeMirror == null || keyTypeMirror.toString().equals("java.lang.Object") ? ArrayTypeName.of(TypeName.BYTE) : TypeName.get(keyTypeMirror).box();
        var valueType = TypeName.get(valueTypeMirror).box();

        var catchesKeyException = keyParameter != null && parameters.stream().anyMatch(p -> p instanceof ConsumerParameter.KeyDeserializationException || p instanceof ConsumerParameter.Exception);
        var catchesValueException = parameters.stream().anyMatch(p -> p instanceof ConsumerParameter.ValueDeserializationException || p instanceof ConsumerParameter.Exception);

        methodBuilder.returns(ParameterizedTypeName.get(recordHandler, keyType, valueType));
        var b = CodeBlock.builder();
        b.add("return (consumer, tctx, record) -> {$>\n");
        if (catchesKeyException) b.add("$T keyException = null;\n", recordKeyDeserializationException);
        if (catchesValueException) b.add("$T valueException = null;\n", recordValueDeserializationException);
        if (keyParameter != null) b.add("$T key = null;\n", keyType);
        b.add("$T value = null;\n", valueType);
        if (catchesKeyException || catchesValueException) b.add("try {$>\n");
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
            } else if (parameter instanceof ConsumerParameter.Unknown) {
                if (keyParameter == null || keySeen) {
                    b.add("value");
                } else {
                    keySeen = true;
                    b.add("key");
                }
            } else {
                throw new ProcessingErrorException(
                    "Record listener can't have parameter of type %s, only consumer, record, record key, record value, exception and record telemetry are allowed".formatted(parameter.element().asType()),
                    parameter.element()
                );
            }
        }
        b.add(");");
        b.add("$<\n};\n");
        var keyTag = keyParameter == null ? Set.<String>of() : TagUtils.parseTagValue(keyParameter.element());
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
                var message = "Kafka listener method has invalid key type %s".formatted(keyTypeMirror);
                throw new ProcessingErrorException(message, executableElement);
            }
        }
        if (valueTypeMirror instanceof WildcardType || valueTypeMirror instanceof IntersectionType || valueTypeMirror instanceof UnionType) {
            var message = "Kafka listener method has invalid value type %s".formatted(valueTypeMirror);
            throw new ProcessingErrorException(message, executableElement);
        }

        var keyType = keyTypeMirror instanceof WildcardType w && w.getSuperBound() == null && w.getExtendsBound() == null ? ArrayTypeName.of(TypeName.BYTE) : TypeName.get(keyTypeMirror);
        var valueType = TypeName.get(valueTypeMirror);

        methodBuilder.returns(ParameterizedTypeName.get(recordsHandler, keyType, valueType));
        var b = CodeBlock.builder();
        b.add("return (consumer, tctx, records) -> {$>\n");
        b.add("controller.$N(", executableElement.getSimpleName());
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) b.add(", ");
            var parameter = parameters.get(i);
            if (parameter instanceof ConsumerParameter.Consumer) {
                b.add("consumer");
            } else if (parameter instanceof ConsumerParameter.RecordsTelemetry) {
                b.add("tctx");
            } else if (parameter instanceof ConsumerParameter.Records) {
                b.add("records");
            } else {
                throw new ProcessingErrorException(
                    "Records listener can't have parameter of type %s, only consumer, records and records telemetry are allowed".formatted(parameter.element().asType()),
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
}
