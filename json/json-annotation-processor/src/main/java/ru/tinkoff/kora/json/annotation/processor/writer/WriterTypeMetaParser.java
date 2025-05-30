package ru.tinkoff.kora.json.annotation.processor.writer;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.common.naming.NameConverter;
import ru.tinkoff.kora.json.annotation.processor.JsonTypes;
import ru.tinkoff.kora.json.annotation.processor.KnownType;
import ru.tinkoff.kora.json.annotation.processor.writer.JsonClassWriterMeta.FieldMeta;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static ru.tinkoff.kora.annotation.processor.common.CommonUtils.getNameConverter;

public class WriterTypeMetaParser {
    private final ProcessingEnvironment env;
    private final Elements elements;
    private final Types types;
    private final KnownType knownTypes;
    private final TypeMirror jsonFieldAnnotation;

    public WriterTypeMetaParser(ProcessingEnvironment env, KnownType knownTypes) {
        this.env = env;
        this.elements = env.getElementUtils();
        this.types = env.getTypeUtils();
        this.knownTypes = knownTypes;
        var jsonFieldElement = this.elements.getTypeElement(JsonTypes.jsonFieldAnnotation.canonicalName());
        this.jsonFieldAnnotation = jsonFieldElement.asType();
    }

    public JsonClassWriterMeta parse(TypeElement jsonClass, TypeMirror typeMirror) {
        if (jsonClass.getKind() != ElementKind.CLASS && jsonClass.getKind() != ElementKind.RECORD) {
            throw new IllegalArgumentException("JsonWriter can be generated only for types that are class/record/sealed, but called for: " + jsonClass);
        }
        if (jsonClass.getModifiers().contains(Modifier.ABSTRACT)) {
            throw new IllegalArgumentException("JsonWriter can't be generated for abstract types, but called for: " + jsonClass);
        }

        var fieldElements = this.parseFields(jsonClass);
        var fieldMetas = new ArrayList<FieldMeta>(fieldElements.size());
        for (var fieldElement : fieldElements) {
            var fieldMeta = this.parseField(jsonClass, fieldElement);
            fieldMetas.add(fieldMeta);
        }
        return new JsonClassWriterMeta(typeMirror, jsonClass, fieldMetas);
    }

    private List<VariableElement> parseFields(TypeElement typeElement) {
        return typeElement.getEnclosedElements()
            .stream()
            .filter(e -> e.getKind() == ElementKind.FIELD)
            .filter(e -> !e.getModifiers().contains(Modifier.STATIC))
            .map(VariableElement.class::cast)
            .filter(v -> AnnotationUtils.findAnnotation(v, JsonTypes.jsonSkipAnnotation) == null)
            .collect(Collectors.toList());
    }


    private FieldMeta parseField(TypeElement jsonClass, VariableElement field) {
        var jsonField = this.findJsonField(field);

        var fieldNameConverter = getNameConverter(jsonClass);
        var fieldTypeMirror = field.asType();
        var jsonName = this.parseJsonName(field, jsonField, fieldNameConverter);
        var accessorMethod = this.getAccessorMethod(jsonClass, field);
        var writer = AnnotationUtils.<TypeMirror>parseAnnotationValueWithoutDefault(jsonField, "writer");

        var typeMeta = this.parseWriterFieldType(fieldTypeMirror);
        var includeType = Optional.ofNullable(AnnotationUtils.findAnnotation(field, JsonTypes.jsonInclude))
            .or(() -> Optional.ofNullable(AnnotationUtils.findAnnotation(jsonClass, JsonTypes.jsonInclude)))
            .map(a -> AnnotationUtils.<VariableElement>parseAnnotationValueWithoutDefault(a, "value").getSimpleName().toString())
            .flatMap(JsonClassWriterMeta.IncludeType::tryParse)
            .orElse(JsonClassWriterMeta.IncludeType.NON_NULL);

        return new FieldMeta(field, fieldTypeMirror, typeMeta, jsonName, includeType, accessorMethod, writer);
    }

    private WriterFieldType parseWriterFieldType(TypeMirror jsonClass) {
        boolean isJsonNullable = false;
        TypeMirror realType = jsonClass;
        if (jsonClass instanceof DeclaredType dt && JsonTypes.jsonNullable.canonicalName().equals((dt.asElement()).toString())) {
            realType = dt.getTypeArguments().get(0);
            isJsonNullable = true;
        }

        var knownType = this.knownTypes.detect(realType);
        if (knownType != null) {
            return new WriterFieldType.KnownWriterFieldType(knownType, realType, isJsonNullable);
        } else {
            return new WriterFieldType.UnknownWriterFieldType(realType, isJsonNullable);
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
        var jsonFieldValue = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(jsonField, "value");
        if (jsonFieldValue != null && !jsonFieldValue.isBlank()) {
            return jsonFieldValue;
        }
        return param.getSimpleName().toString();
    }

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
        throw new ProcessingErrorException("Can't detect accessor method name: %s".formatted(paramName), param);
    }
}
