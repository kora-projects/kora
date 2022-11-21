package ru.tinkoff.kora.json.annotation.processor.writer;

import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.common.naming.NameConverter;
import ru.tinkoff.kora.json.annotation.processor.JsonUtils;
import ru.tinkoff.kora.json.annotation.processor.KnownType;
import ru.tinkoff.kora.json.annotation.processor.writer.JsonClassWriterMeta.FieldMeta;
import ru.tinkoff.kora.json.common.annotation.JsonField;
import ru.tinkoff.kora.json.common.annotation.JsonSkip;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppUtils;

import javax.annotation.Nullable;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static ru.tinkoff.kora.annotation.processor.common.CommonUtils.getNameConverter;

public class WriterTypeMetaParser {
    private final ProcessingEnvironment env;
    private final Elements elements;
    private final Types types;
    private final KnownType knownTypes;
    private final TypeMirror jsonFieldAnnotation;
    private final Map<String, ExecutableElement> jsonFieldMethods;
    private final TypeMirror defaultWriter;

    public WriterTypeMetaParser(ProcessingEnvironment env, KnownType knownTypes) {
        this.env = env;
        this.elements = env.getElementUtils();
        this.types = env.getTypeUtils();
        this.knownTypes = knownTypes;
        var jsonFieldElement = this.elements.getTypeElement(JsonField.class.getCanonicalName());
        this.jsonFieldAnnotation = jsonFieldElement.asType();
        this.jsonFieldMethods = jsonFieldValues(jsonFieldElement);
        this.defaultWriter = this.elements.getTypeElement(JsonField.DefaultWriter.class.getCanonicalName()).asType();
    }

    private static Map<String, ExecutableElement> jsonFieldValues(TypeElement jsonField) {
        return jsonField.getEnclosedElements()
            .stream()
            .filter(e -> e.getKind() == ElementKind.METHOD)
            .map(ExecutableElement.class::cast)
            .collect(Collectors.toMap(e -> e.getSimpleName().toString(), e -> e));
    }

    @Nullable
    public JsonClassWriterMeta parse(TypeMirror typeMirror) {
        var jsonClass = (TypeElement) this.types.asElement(typeMirror);
        if (jsonClass == null) {
            return null;
        }

        var discriminatorField = JsonUtils.discriminator(this.types, jsonClass);

        var fieldElements = this.parseFields(jsonClass);
        var error = false;
        var fieldMetas = new ArrayList<FieldMeta>(fieldElements.size());
        for (var fieldElement : fieldElements) {
            var fieldMeta = this.parseField(jsonClass, fieldElement);
            if (fieldMeta == null) {
                error = true;
            } else {
                fieldMetas.add(fieldMeta);
            }
        }
        if (error) {
            return null;
        }
        return new JsonClassWriterMeta(
            typeMirror, jsonClass, fieldMetas, discriminatorField, jsonClass.getModifiers().contains(Modifier.SEALED)
        );
    }

    private List<VariableElement> parseFields(TypeElement typeElement) {
        return typeElement.getEnclosedElements()
            .stream()
            .filter(e -> e.getKind() == ElementKind.FIELD)
            .filter(e -> !e.getModifiers().contains(Modifier.STATIC))
            .map(VariableElement.class::cast)
            .filter(v -> v.getAnnotation(JsonSkip.class) == null)
            .collect(Collectors.toList());
    }


    @Nullable
    private FieldMeta parseField(TypeElement jsonClass, VariableElement field) {
        var jsonField = this.findJsonField(field);

        var fieldNameConverter = getNameConverter(jsonClass);
        if (field.asType().getKind() == TypeKind.ERROR) {
            env.getMessager().printMessage(Diagnostic.Kind.ERROR, "Field %s.%s is ERROR".formatted(jsonClass, field.getSimpleName()), field);
            return null;
        }
        var jsonName = this.parseJsonName(field, jsonField, fieldNameConverter);
        var accessorMethod = this.getAccessorMethod(jsonClass, field);
        if (accessorMethod == null) {
            return null;
        }
        var writerFieldValue = (TypeMirror) CommonUtils.parseAnnotationValue(this.elements, jsonField, "writer");


        var writer = writerFieldValue == null ? null
            : this.types.isSameType(writerFieldValue, this.defaultWriter) ? null
            : writerFieldValue;

        var typeMeta = this.parseWriterFieldType(field.asType());


        return new FieldMeta(field, field.asType(), typeMeta, jsonName, accessorMethod, writer);
    }

    private WriterFieldType parseWriterFieldType(TypeMirror typeMirror) {
        var knownType = this.knownTypes.detect(typeMirror);
        if (knownType != null) {
            return new WriterFieldType.KnownWriterFieldType(knownType);
        } else {
            return new WriterFieldType.UnknownWriterFieldType(typeMirror);
        }
    }

    @Nullable
    private AnnotationMirror findJsonField(VariableElement param) {
        return param.getAnnotationMirrors()
            .stream()
            .filter(a -> this.types.isSameType(a.getAnnotationType(), this.jsonFieldAnnotation))
            .findFirst()
            .orElse(null);

    }

    private String parseJsonName(VariableElement param, @Nullable AnnotationMirror jsonField, @Nullable NameConverter nameConverter) {
        if (jsonField == null) {
            if (nameConverter != null) {
                return nameConverter.convert(param.getSimpleName().toString());
            } else {
                return param.getSimpleName().toString();
            }
        }
        var jsonFieldValue = (String) CommonUtils.parseAnnotationValue(this.elements, jsonField, "value");
        if (jsonFieldValue != null && !jsonFieldValue.isBlank()) {
            return jsonFieldValue;
        }
        return param.getSimpleName().toString();
    }

    @Nullable
    private ExecutableElement getAccessorMethod(TypeElement jsonClass, VariableElement param) {
        var paramName = param.getSimpleName().toString();
        var capitalizedParamName = Character.toUpperCase(paramName.charAt(0)) + paramName.substring(1);

        var accessorMethodName = jsonClass.getEnclosedElements().stream()
            .filter(e -> e.getKind() == ElementKind.METHOD)
            .map(ExecutableElement.class::cast)
            .filter(e -> e.getParameters().isEmpty())
            .filter(e -> {
                var methodName = e.getSimpleName().toString();
                return methodName.equals(paramName) || methodName.equals("get" + capitalizedParamName);
            })
            .filter(e -> this.types.isSameType(e.getReturnType(), param.asType()))
            .findFirst();
        if (accessorMethodName.isPresent()) {
            return accessorMethodName.get();
        }
        this.env.getMessager().printMessage(Diagnostic.Kind.ERROR, "Can't detect accessor method name: %s".formatted(paramName), param);
        return null;
    }

}
