package ru.tinkoff.kora.kafka.annotation.processor.utils;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.NameUtils;
import ru.tinkoff.kora.kafka.annotation.processor.KafkaClassNames;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static ru.tinkoff.kora.annotation.processor.common.CommonUtils.capitalize;
import static ru.tinkoff.kora.annotation.processor.common.CommonUtils.decapitalize;

public final class KafkaUtils {

    private KafkaUtils() {}

    public static String prepareConsumerTagName(ExecutableElement method) {
        var controllerName = method.getEnclosingElement().getSimpleName().toString();
        var methodName = method.getSimpleName().toString();
        return capitalize(controllerName) + capitalize(methodName) + "Tag";
    }

    public static ClassName prepareConsumerTag(ExecutableElement method) {
        String moduleName = method.getEnclosingElement().getSimpleName().toString() + "Module";
        return ClassName.get(NameUtils.getPackageName(method), moduleName, prepareConsumerTagName(method));
    }

    @Nullable
    public static Set<TypeName> findConsumerUserTags(ExecutableElement method) {
        AnnotationMirror annotation = AnnotationUtils.findAnnotation(method, KafkaClassNames.kafkaListener);
        return Optional.ofNullable(AnnotationUtils.<List<TypeMirror>>parseAnnotationValueWithoutDefault(annotation, "tag"))
            .filter(t -> !t.isEmpty())
            .map(t -> t.stream()
                .map(TypeName::get)
                .collect(Collectors.toSet()))
            .orElse(null);
    }

    public static Set<TypeName> getConsumerTags(ExecutableElement method) {
        var tags = findConsumerUserTags(method);
        if (tags == null) {
            return Set.of(prepareConsumerTag(method));
        } else {
            return new HashSet<>(tags);
        }
    }

    public static String prepareMethodName(ExecutableElement method, String suffix) {
        var controllerName = method.getEnclosingElement().getSimpleName().toString();
        var methodName = method.getSimpleName().toString();
        return decapitalize(controllerName) + capitalize(methodName) + suffix;
    }

    public static boolean isConsumerRecord(TypeMirror tm) {
        return tm instanceof DeclaredType dt && ClassName.get((TypeElement) dt.asElement()).equals(KafkaClassNames.consumerRecord);
    }

    public static boolean isConsumerRecords(TypeMirror tm) {
        return tm instanceof DeclaredType dt && ClassName.get((TypeElement) dt.asElement()).equals(KafkaClassNames.consumerRecords);
    }

    public static boolean isProducerRecord(TypeMirror tm) {
        return tm instanceof DeclaredType dt && ClassName.get((TypeElement) dt.asElement()).equals(KafkaClassNames.producerRecord);
    }

    public static boolean isProducerCallback(TypeMirror tm) {
        return tm instanceof DeclaredType dt && ClassName.get((TypeElement) dt.asElement()).equals(KafkaClassNames.producerCallback);
    }

    public static boolean isHeaders(TypeMirror tm) {
        return tm instanceof DeclaredType dt && ClassName.get((TypeElement) dt.asElement()).equals(KafkaClassNames.headers);
    }

    public static boolean isHeader(TypeMirror tm) {
        return tm instanceof DeclaredType dt && ClassName.get((TypeElement) dt.asElement()).equals(KafkaClassNames.header);
    }

    public static boolean isKeyDeserializationException(TypeMirror tm) {
        return tm instanceof DeclaredType dt && ClassName.get((TypeElement) dt.asElement()).equals(KafkaClassNames.recordKeyDeserializationException);
    }

    public static boolean isValueDeserializationException(TypeMirror tm) {
        return tm instanceof DeclaredType dt && ClassName.get((TypeElement) dt.asElement()).equals(KafkaClassNames.recordValueDeserializationException);
    }

    public static boolean isAnyException(TypeMirror tm) {
        return tm.toString().equals("java.lang.Throwable") || tm.toString().equals("java.lang.Exception");
    }

    public static boolean isRecordsTelemetry(TypeMirror tm) {
        return tm instanceof DeclaredType dt && ClassName.get((TypeElement) dt.asElement()).equals(KafkaClassNames.kafkaConsumerRecordsTelemetry);
    }

    public static boolean isConsumer(TypeMirror tm) {
        return tm instanceof DeclaredType dt && ClassName.get((TypeElement) dt.asElement()).equals(KafkaClassNames.consumer);
    }

}
