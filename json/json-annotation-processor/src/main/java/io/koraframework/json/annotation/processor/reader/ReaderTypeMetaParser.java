package io.koraframework.json.annotation.processor.reader;

import com.palantir.javapoet.TypeName;
import org.jspecify.annotations.Nullable;
import io.koraframework.annotation.processor.common.AnnotationUtils;
import io.koraframework.annotation.processor.common.CommonUtils;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import io.koraframework.json.annotation.processor.JsonTypes;
import io.koraframework.json.annotation.processor.JsonUtils;
import io.koraframework.json.annotation.processor.KnownType;
import io.koraframework.json.annotation.processor.reader.JsonClassReaderMeta.FieldMeta;
import io.koraframework.json.annotation.processor.reader.ReaderFieldType.KnownTypeReaderMeta;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import java.util.ArrayList;
import java.util.Objects;

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
            throw new ProcessingErrorException("""
                JsonReader can't be generated for type:
                  %s

                Problem:
                  @JsonReader can be generated only for concrete classes and records.

                Hint:
                  Interfaces, annotations, enums, primitives, and arrays don't have a constructor that can be used to create an object from JSON.

                Fix:
                  Move @JsonReader/@Json to a concrete class or record, or provide a custom JsonReader<%s> component.
                """.formatted(jsonClass, jsonClass), jsonClass);
        }
        if (jsonClass.getModifiers().contains(Modifier.ABSTRACT)) {
            throw new ProcessingErrorException("""
                JsonReader can't be generated for abstract type:
                  %s

                Problem:
                  Abstract classes can't be instantiated while reading JSON.

                Hint:
                  Kora can generate readers for concrete classes and records. Polymorphic sealed hierarchies must use the supported sealed JSON configuration.

                Fix:
                  Make the type concrete, use a supported sealed hierarchy, or provide a custom JsonReader<%s> component.
                """.formatted(jsonClass, jsonClass), jsonClass);
        }

        var jsonConstructor = Objects.requireNonNull(this.findJsonConstructor(jsonClass));
        var fields = new ArrayList<FieldMeta>(jsonConstructor.getParameters().size());
        var nameConverter = CommonUtils.getNameConverter(jsonClass);

        for (var parameter : jsonConstructor.getParameters()) {
            var fieldMeta = this.parseField(jsonClass, parameter, nameConverter);
            fields.add(fieldMeta);
        }
        return new JsonClassReaderMeta(typeMirror, jsonClass, fields);
    }

    @Nullable
    public ReaderFieldType parseReaderFieldType(TypeMirror jsonClass) {
        var isJsonNullable = false;
        var realType = jsonClass;
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
            throw new ProcessingErrorException(jsonConstructorError(typeElement, "No public constructor was found."), typeElement);
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
            throw new ProcessingErrorException(jsonConstructorError(typeElement, "More than one public constructor is annotated with @JsonReader."), typeElement);
        }

        var jsonConstructors = constructors.stream()
            .filter(e -> AnnotationUtils.findAnnotation(e, JsonTypes.json) != null)
            .toList();
        if (jsonConstructors.size() == 1) {
            return jsonConstructors.get(0);
        }
        if (!jsonConstructors.isEmpty()) {
            throw new ProcessingErrorException(jsonConstructorError(typeElement, "More than one public constructor is annotated with @Json."), typeElement);
        }

        var nonEmpty = constructors.stream()
            .filter(c -> !c.getParameters().isEmpty())
            .toList();
        if (nonEmpty.size() == 1) {
            return nonEmpty.get(0);
        }
        throw new ProcessingErrorException(jsonConstructorError(typeElement, "There are multiple possible public constructors and none is selected explicitly."), typeElement);
    }

    private static String jsonConstructorError(TypeElement typeElement, String problem) {
        return """
            JsonReader can't choose a constructor for type:
              %s

            Problem:
              %s

            Hint:
              JsonReader generation needs exactly one constructor to create the object.

            Fix:
              Keep a single public constructor, or mark exactly one public constructor with @JsonReader or @Json.
            """.formatted(typeElement, problem);
    }

    private FieldMeta parseField(TypeElement jsonClass, VariableElement parameter, CommonUtils.NameConverter nameConverter) {
        var jsonField = this.findJsonField(jsonClass, parameter);
        var jsonName = this.parseJsonName(parameter, jsonField, nameConverter);
        var reader = CommonUtils.parseMapping(parameter).getMapping(JsonTypes.jsonReader);
        if (reader == null) {
            var field = JsonUtils.fieldByParameter(jsonClass, parameter);
            if (field != null) {
                reader = CommonUtils.parseMapping(field).getMapping(JsonTypes.jsonReader);
            }
        }
        var typeMeta = this.parseReaderFieldType(parameter.asType());
        return new FieldMeta(parameter, jsonName, TypeName.get(parameter.asType()), typeMeta, reader);
    }

    @Nullable
    private AnnotationMirror findJsonField(TypeElement jsonClass, VariableElement param) {
        var paramJsonField = AnnotationUtils.findAnnotation(param, JsonTypes.jsonFieldAnnotation);
        if (paramJsonField != null) {
            return paramJsonField;
        }
        var field = JsonUtils.fieldByParameter(jsonClass, param);
        if (field == null) {
            return null;
        }
        return AnnotationUtils.findAnnotation(field, JsonTypes.jsonFieldAnnotation);
    }

    private String parseJsonName(VariableElement param, @Nullable AnnotationMirror jsonField, CommonUtils.@Nullable NameConverter nameConverter) {
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
