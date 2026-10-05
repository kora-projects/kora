package io.koraframework.kafka.annotation.processor.utils;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.TypeName;
import org.jspecify.annotations.Nullable;
import io.koraframework.annotation.processor.common.AnnotationUtils;
import io.koraframework.annotation.processor.common.NameUtils;
import io.koraframework.kafka.annotation.processor.KafkaClassNames;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

import static io.koraframework.annotation.processor.common.CommonUtils.capitalize;
import static io.koraframework.annotation.processor.common.CommonUtils.decapitalize;

public final class KafkaUtils {

    private KafkaUtils() {}

    /**
     * Listener methods are declared in arbitrary components that can have modules of other kinds generated for them,
     * e.g. for scheduled methods, so module name has listener in it: <code>$MyListeners_KafkaListenerModule</code>
     */
    public static String listenerModuleName(Element listenerType) {
        return NameUtils.generatedType(listenerType, "KafkaListenerModule");
    }

    /**
     * Tag is nested into the module that already has listener type in its name, so it is named only after the method
     */
    public static String prepareConsumerTagName(ExecutableElement method) {
        var methodName = method.getSimpleName().toString();
        return capitalize(methodName) + "Tag";
    }

    public static ClassName prepareConsumerTag(Elements elements, ExecutableElement method) {
        String moduleName = listenerModuleName(method.getEnclosingElement());
        return ClassName.get(elements.getPackageOf(method).getQualifiedName().toString(), moduleName, prepareConsumerTagName(method));
    }

    @Nullable
    public static ClassName findConsumerUserTag(ExecutableElement method) {
        var annotation = AnnotationUtils.findAnnotation(method, KafkaClassNames.kafkaListener);
        var tag = AnnotationUtils.<TypeMirror>parseAnnotationValueWithoutDefault(annotation, "tag");
        if (tag == null) {
            return null;
        } else {
            return (ClassName) TypeName.get(tag);
        }
    }

    @Nullable
    public static ClassName getConsumerTag(Elements elements, ExecutableElement method) {
        var tags = findConsumerUserTag(method);
        if (tags == null) {
            return prepareConsumerTag(elements, method);
        } else {
            return tags;
        }
    }

    public static String prepareMethodName(ExecutableElement method, String suffix) {
        var controllerName = method.getEnclosingElement().getSimpleName().toString();
        var methodName = method.getSimpleName().toString();
        return decapitalize(controllerName) + "_" + methodName + "_" + suffix;
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
        if (!(tm instanceof DeclaredType dt)) {
            return false;
        }

        // toString() would carry type-use annotations, e.g. "java.lang.@Nullable Exception"
        var qualifiedName = ((TypeElement) dt.asElement()).getQualifiedName().toString();
        return qualifiedName.equals("java.lang.Throwable") || qualifiedName.equals("java.lang.Exception");
    }

    public static boolean isConsumer(TypeMirror tm) {
        return tm instanceof DeclaredType dt && ClassName.get((TypeElement) dt.asElement()).equals(KafkaClassNames.consumer);
    }

}
