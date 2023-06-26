package ru.tinkoff.kora.kafka.annotation.processor.utils;

import com.squareup.javapoet.ClassName;
import ru.tinkoff.kora.kafka.annotation.processor.KafkaClassNames;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import static ru.tinkoff.kora.annotation.processor.common.CommonUtils.capitalize;
import static ru.tinkoff.kora.annotation.processor.common.CommonUtils.decapitalize;

public class KafkaUtils {

    public static String prepareTagName(ExecutableElement method) {
        var controllerName = method.getEnclosingElement().getSimpleName().toString();
        var methodName = method.getSimpleName().toString();
        return capitalize(controllerName) + capitalize(methodName) + "Tag";
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
