package io.koraframework.json.annotation.processor.writer;

import org.jspecify.annotations.Nullable;
import io.koraframework.annotation.processor.common.AnnotationUtils;
import io.koraframework.annotation.processor.common.CommonUtils;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import io.koraframework.json.annotation.processor.JsonTypes;
import io.koraframework.json.annotation.processor.KnownType;
import io.koraframework.json.annotation.processor.writer.JsonClassWriterMeta.FieldMeta;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.koraframework.annotation.processor.common.CommonUtils.getNameConverter;

public class WriterTypeMetaParser {
    private final Types types;
    private final KnownType knownTypes;

    public WriterTypeMetaParser(ProcessingEnvironment env, KnownType knownTypes) {
        this.types = env.getTypeUtils();
        this.knownTypes = knownTypes;
    }

    public JsonClassWriterMeta parse(TypeElement jsonClass, TypeMirror typeMirror) {
        if (jsonClass.getKind() != ElementKind.CLASS && jsonClass.getKind() != ElementKind.RECORD) {
            throw new ProcessingErrorException("""
                JsonWriter can't be generated for type:
                  %s

                Problem:
                  @JsonWriter can be generated only for concrete classes and records.

                Hint:
                  Interfaces, annotations, enums, primitives, and arrays don't expose object fields that can be written as JSON.

                Fix:
                  Move @JsonWriter/@Json to a concrete class or record, or provide a custom JsonWriter<%s> component.
                """.formatted(jsonClass, jsonClass), jsonClass);
        }
        if (jsonClass.getModifiers().contains(Modifier.ABSTRACT)) {
            throw new ProcessingErrorException("""
                JsonWriter can't be generated for abstract type:
                  %s

                Problem:
                  Abstract classes don't define a complete concrete JSON shape.

                Hint:
                  Kora can generate writers for concrete classes and records. Polymorphic sealed hierarchies must use the supported sealed JSON configuration.

                Fix:
                  Make the type concrete, use a supported sealed hierarchy, or provide a custom JsonWriter<%s> component.
                """.formatted(jsonClass, jsonClass), jsonClass);
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
        var jsonField = AnnotationUtils.findAnnotation(field, JsonTypes.jsonFieldAnnotation);

        var fieldNameConverter = getNameConverter(jsonClass);
        var fieldTypeMirror = field.asType();
        var jsonName = this.parseJsonName(field, jsonField, fieldNameConverter);
        var accessorMethod = this.getAccessorMethod(jsonClass, field);
        var writer = CommonUtils.parseMapping(field).getMapping(JsonTypes.jsonWriter);

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

    private String parseJsonName(VariableElement param, @Nullable AnnotationMirror jsonField, CommonUtils.@Nullable NameConverter nameConverter) {
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
        throw new ProcessingErrorException("""
            JsonWriter can't find an accessor for field:
              %s.%s

            Problem:
              No zero-argument method named '%s' or 'get%s' returns the field type.

            Hint:
              Private fields need a visible accessor so generated JsonWriter code can read the value.

            Fix:
              Add a public accessor method, make the field accessible, or exclude it with @JsonSkip.
            """.formatted(jsonClass, paramName, paramName, capitalizedParamName), param);
    }
}
