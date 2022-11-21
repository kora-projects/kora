package ru.tinkoff.kora.json.annotation.processor.reader;

import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.common.naming.NameConverter;
import ru.tinkoff.kora.json.annotation.processor.JsonUtils;
import ru.tinkoff.kora.json.annotation.processor.KnownType;
import ru.tinkoff.kora.json.annotation.processor.reader.JsonClassReaderMeta.FieldMeta;
import ru.tinkoff.kora.json.annotation.processor.reader.ReaderFieldType.KnownTypeReaderMeta;
import ru.tinkoff.kora.json.common.annotation.Json;
import ru.tinkoff.kora.json.common.annotation.JsonField;
import ru.tinkoff.kora.json.common.annotation.JsonReader;

import javax.annotation.Nullable;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
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

public class ReaderTypeMetaParser {
    private final ProcessingEnvironment env;
    private final Elements elements;
    private final Types types;
    private final KnownType knownTypes;
    private final TypeMirror jsonFieldAnnotation;
    private final Map<String, ExecutableElement> jsonFieldMethods;
    private final TypeMirror defaultReader;
    private final DeclaredType enumType;

    public ReaderTypeMetaParser(ProcessingEnvironment env, KnownType knownTypes) {
        this.env = env;
        this.elements = env.getElementUtils();
        this.types = env.getTypeUtils();
        this.knownTypes = knownTypes;
        var jsonFieldElement = this.elements.getTypeElement(JsonField.class.getCanonicalName());
        this.jsonFieldAnnotation = jsonFieldElement.asType();
        this.jsonFieldMethods = jsonFieldValues(jsonFieldElement);
        this.defaultReader = this.elements.getTypeElement(JsonField.DefaultReader.class.getCanonicalName()).asType();
        this.enumType = this.types.getDeclaredType(
            this.elements.getTypeElement(Enum.class.getCanonicalName()),
            this.types.getWildcardType(null, null)
        );

    }

    private static Map<String, ExecutableElement> jsonFieldValues(TypeElement jsonField) {
        return jsonField.getEnclosedElements()
            .stream()
            .filter(e -> e.getKind() == ElementKind.METHOD)
            .map(ExecutableElement.class::cast)
            .collect(Collectors.toMap(e -> e.getSimpleName().toString(), e -> e));
    }


    @Nullable
    public JsonClassReaderMeta parse(TypeMirror typeMirror) {
        var jsonClass = (TypeElement) this.types.asElement(typeMirror);
        if (jsonClass == null) {
            return null;
        }
        if (this.types.isAssignable(typeMirror, this.enumType)) {
            return new JsonClassReaderMeta(typeMirror, jsonClass, List.of(), null, false);
        }

        var discriminatorField = JsonUtils.discriminator(this.types, jsonClass);

        if (jsonClass.getKind().isInterface() && (jsonClass.getModifiers().contains(Modifier.SEALED))) {
            return new JsonClassReaderMeta(typeMirror, jsonClass, Collections.emptyList(), discriminatorField, true);
        }
        var jsonConstructor = this.findJsonConstructor(jsonClass);
        if (jsonConstructor == null) {
            this.env.getMessager().printMessage(Diagnostic.Kind.ERROR, "Class: %s\nTo generate json reader class must have one public constructor or constructor annotated with any of @Json/@JsonReader"
                    .formatted(jsonClass),
                jsonClass
            );
            return null;
        }

        var fields = new ArrayList<FieldMeta>(jsonConstructor.getParameters().size());
        var error = false;

        var nameConverter = getNameConverter(jsonClass);

        for (var parameter : jsonConstructor.getParameters()) {
            var fieldMeta = this.parseField(jsonClass, parameter, nameConverter);
            if (fieldMeta == null) {
                error = true;
            } else {
                fields.add(fieldMeta);
            }
        }
        if (error) {
            return null;
        }
        return new JsonClassReaderMeta(typeMirror, jsonClass, fields, discriminatorField, false);
    }

    @Nullable
    public ReaderFieldType parseReaderFieldType(TypeMirror jsonClass) {
        var knownType = this.knownTypes.detect(jsonClass);
        if (knownType != null) {
            return new KnownTypeReaderMeta(knownType, jsonClass);
        } else {
            return new ReaderFieldType.UnknownTypeReaderMeta(jsonClass);
        }
    }

    @Nullable
    private ExecutableElement findJsonConstructor(TypeElement typeElement) {
        var constructors = typeElement.getEnclosedElements()
            .stream()
            .filter(e -> e.getKind() == ElementKind.CONSTRUCTOR)
            .filter(e -> e.getModifiers().contains(Modifier.PUBLIC))
            .map(ExecutableElement.class::cast)
            .toList();
        if (constructors.isEmpty()) {
            this.env.getMessager().printMessage(Diagnostic.Kind.ERROR, "No public constructor found: " + typeElement, typeElement);
            return null;
        }
        if (constructors.size() == 1) {
            return constructors.get(0);
        }

        var jsonConstructors = constructors.stream()
            .filter(e -> e.getAnnotation(JsonReader.class) != null || e.getAnnotation(Json.class) != null)
            .toList();
        if (jsonConstructors.size() == 1) {
            return jsonConstructors.get(0);
        }
        var nonEmpty = constructors.stream()
            .filter(c -> !c.getParameters().isEmpty())
            .toList();
        if (nonEmpty.size() == 1) {
            return nonEmpty.get(0);
        }

        this.env.getMessager().printMessage(Diagnostic.Kind.ERROR, "More than one public constructor found and none of them is annotated with @JsonReader or @Json: " + typeElement, typeElement);
        return null;

    }


    private FieldMeta parseField(TypeElement jsonClass, VariableElement parameter, NameConverter nameConverter) {
        var jsonField = this.findJsonField(jsonClass, parameter);
        if (parameter.asType().getKind() == TypeKind.ERROR) {
            env.getMessager().printMessage(Diagnostic.Kind.ERROR, "Field %s.%s is ERROR".formatted(jsonClass, parameter.getSimpleName()), parameter);
            return null;
        }

        var jsonName = this.parseJsonName(parameter, jsonField, nameConverter);
        var readerFieldValue = (TypeMirror) CommonUtils.parseAnnotationValue(this.elements, jsonField, "reader");

        var reader = readerFieldValue == null ? null
            : this.types.isSameType(readerFieldValue, this.defaultReader) ? null
            : readerFieldValue;

        var typeMeta = this.parseReaderFieldType(parameter.asType());


        return new FieldMeta(parameter, jsonName, parameter.asType(), typeMeta, reader);
    }

    @Nullable
    private AnnotationMirror findJsonField(TypeElement jsonClass, VariableElement param) {
        var paramJsonField = CommonUtils.findAnnotation(this.elements, this.types, param, this.jsonFieldAnnotation);
        if (paramJsonField != null) {
            return paramJsonField;
        }

        for (var e : jsonClass.getEnclosedElements()) {
            if (e.getKind() != ElementKind.FIELD) {
                continue;
            }
            if (!e.getSimpleName().toString().equals(param.getSimpleName().toString())) {
                continue;
            }
            var variableElement = (VariableElement) e;
            if (types.isSameType(variableElement.asType(), param.asType())) {
                return CommonUtils.findAnnotation(this.elements, this.types, variableElement, this.jsonFieldAnnotation);
            }
        }
        return null;
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
}
