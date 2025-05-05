package ru.tinkoff.kora.json.annotation.processor.reader;

import com.squareup.javapoet.TypeName;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.common.naming.NameConverter;
import ru.tinkoff.kora.json.annotation.processor.JsonTypes;
import ru.tinkoff.kora.json.annotation.processor.KnownType;
import ru.tinkoff.kora.json.annotation.processor.reader.JsonClassReaderMeta.FieldMeta;
import ru.tinkoff.kora.json.annotation.processor.reader.ReaderFieldType.KnownTypeReaderMeta;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import java.util.ArrayList;
import java.util.Objects;

import static ru.tinkoff.kora.annotation.processor.common.CommonUtils.getNameConverter;

public class ReaderTypeMetaParser {
    private final ProcessingEnvironment env;
    private final Elements elements;
    private final KnownType knownTypes;

    public ReaderTypeMetaParser(ProcessingEnvironment env, KnownType knownTypes) {
        this.env = env;
        this.elements = env.getElementUtils();
        this.knownTypes = knownTypes;
    }

    public JsonClassReaderMeta parse(TypeElement jsonClass, TypeMirror typeMirror) throws ProcessingErrorException {
        if (jsonClass.getKind() != ElementKind.CLASS && jsonClass.getKind() != ElementKind.RECORD) {
            throw new IllegalArgumentException("JsonReader can be generated only for types that are class/record/sealed, but called for: " + jsonClass);
        }
        if (jsonClass.getModifiers().contains(Modifier.ABSTRACT)) {
            throw new IllegalArgumentException("JsonReader can't be generated for abstract types, but called for: " + jsonClass);
        }

        var jsonConstructor = Objects.requireNonNull(this.findJsonConstructor(jsonClass));
        var fields = new ArrayList<FieldMeta>(jsonConstructor.getParameters().size());
        var nameConverter = getNameConverter(jsonClass);

        for (var parameter : jsonConstructor.getParameters()) {
            var fieldMeta = this.parseField(jsonClass, parameter, nameConverter);
            fields.add(fieldMeta);
        }
        return new JsonClassReaderMeta(typeMirror, jsonClass, fields);
    }

    @Nullable
    public ReaderFieldType parseReaderFieldType(TypeMirror jsonClass) {
        boolean isJsonNullable = false;
        TypeMirror realType = jsonClass;
        if (jsonClass instanceof DeclaredType dt && JsonTypes.jsonNullable.canonicalName().equals((dt.asElement()).toString())) {
            realType = dt.getTypeArguments().get(0);
            isJsonNullable = true;
        }

        var knownType = this.knownTypes.detect(realType);
        if (knownType != null) {
            return new KnownTypeReaderMeta(knownType, realType, isJsonNullable);
        } else {
            return new ReaderFieldType.UnknownTypeReaderMeta(realType, isJsonNullable);
        }
    }

    private ExecutableElement findJsonConstructor(TypeElement typeElement) {
        var constructors = typeElement.getEnclosedElements()
            .stream()
            .filter(e -> e.getKind() == ElementKind.CONSTRUCTOR)
            .filter(e -> e.getModifiers().contains(Modifier.PUBLIC))
            .map(ExecutableElement.class::cast)
            .toList();

        if (constructors.isEmpty()) {
            throw new ProcessingErrorException("Class: %s\nIn order to generate JsonReader class must have one public constructor or constructor annotated with any of @Json/@JsonReader"
                .formatted(typeElement),
                typeElement
            );
        }
        if (constructors.size() == 1) {
            return constructors.get(0);
        }

        var jsonReaderConstructors = constructors.stream()
            .filter(e -> AnnotationUtils.findAnnotation(e, JsonTypes.jsonReaderAnnotation) != null)
            .toList();
        if (jsonReaderConstructors.size() == 1) {
            return jsonReaderConstructors.get(0);
        }
        if (!jsonReaderConstructors.isEmpty()) {
            throw new ProcessingErrorException("Class: %s\nIn order to generate JsonReader class must have one public constructor or constructor annotated with any of @Json/@JsonReader"
                .formatted(typeElement),
                typeElement
            );
        }

        var jsonConstructors = constructors.stream()
            .filter(e -> AnnotationUtils.findAnnotation(e, JsonTypes.json) != null)
            .toList();
        if (jsonConstructors.size() == 1) {
            return jsonConstructors.get(0);
        }
        if (!jsonConstructors.isEmpty()) {
            throw new ProcessingErrorException("Class: %s\nIn order to generate JsonReader class must have one public constructor or constructor annotated with any of @Json/@JsonReader"
                .formatted(typeElement),
                typeElement
            );
        }

        var nonEmpty = constructors.stream()
            .filter(c -> !c.getParameters().isEmpty())
            .toList();
        if (nonEmpty.size() == 1) {
            return nonEmpty.get(0);
        }
        throw new ProcessingErrorException("Class: %s\nIn order to generate JsonReader class must have one public constructor or constructor annotated with any of @Json/@JsonReader"
            .formatted(typeElement),
            typeElement
        );
    }

    private FieldMeta parseField(TypeElement jsonClass, VariableElement parameter, NameConverter nameConverter) {
        var jsonField = this.findJsonField(jsonClass, parameter);
        var jsonName = this.parseJsonName(parameter, jsonField, nameConverter);
        var reader = AnnotationUtils.<TypeMirror>parseAnnotationValueWithoutDefault(jsonField, "reader");
        var typeMeta = this.parseReaderFieldType(parameter.asType());
        return new FieldMeta(parameter, jsonName, TypeName.get(parameter.asType()), typeMeta, reader);
    }

    @Nullable
    private AnnotationMirror findJsonField(TypeElement jsonClass, VariableElement param) {
        var paramJsonField = AnnotationUtils.findAnnotation(param, JsonTypes.jsonFieldAnnotation);
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
            return AnnotationUtils.findAnnotation(e, JsonTypes.jsonFieldAnnotation);
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

        var jsonFieldValue = (String) AnnotationUtils.parseAnnotationValue(this.elements, jsonField, "value");
        if (jsonFieldValue != null && !jsonFieldValue.isBlank()) {
            return jsonFieldValue;
        }
        return param.getSimpleName().toString();
    }
}
