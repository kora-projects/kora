package io.koraframework.database.annotation.processor;

import io.koraframework.annotation.processor.common.AnnotationUtils;
import io.koraframework.annotation.processor.common.CommonUtils;
import io.koraframework.annotation.processor.common.ProcessingError;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import org.jspecify.annotations.Nullable;

import javax.lang.model.element.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EntityUtils {

    public final static CommonUtils.NameConverter SNAKE_CASE_NAME_CONVERTER = originalName -> {
        var splitted = originalName.split("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|( +)");
        return Stream.of(splitted)
            .map(String::toLowerCase)
            .collect(Collectors.joining("_"));
    };

    public static String parseColumnName(VariableElement element, CommonUtils.@Nullable NameConverter columnsNameConverter) {
        var column = AnnotationUtils.findAnnotation(element, DbUtils.COLUMN_ANNOTATION);
        var fieldName = element.getSimpleName().toString();
        if (column != null) {
            return AnnotationUtils.parseAnnotationValueWithoutDefault(column, "value");
        }
        return columnsNameConverter == null
            ? SNAKE_CASE_NAME_CONVERTER.convert(fieldName)
            : columnsNameConverter.convert(fieldName);
    }

    public static ExecutableElement findEntityConstructor(TypeElement type) throws ProcessingErrorException {
        var constructors = type.getEnclosedElements()
            .stream()
            .filter(e -> e.getKind() == ElementKind.CONSTRUCTOR)
            .map(ExecutableElement.class::cast)
            .filter(e -> e.getModifiers().contains(Modifier.PUBLIC))
            .toList();
        if (constructors.isEmpty()) {
            throw new ProcessingErrorException(List.of(new ProcessingError(entityConstructorError(type, "No public constructor was found.", "Add a public constructor that accepts all required entity fields."), type)));
        }
        if (constructors.size() == 1) {
            return constructors.get(0);
        }
        var entityConstructors = constructors.stream()
            .filter(c -> AnnotationUtils.findAnnotation(c, DbUtils.ENTITY_CONSTRUCTOR_ANNOTATION) != null)
            .toList();
        if (entityConstructors.isEmpty()) {
            throw new ProcessingErrorException(List.of(new ProcessingError(entityConstructorError(type, "More than one public constructor exists and none is marked with @EntityConstructor.", "Mark exactly one public constructor with @EntityConstructor."), type)));
        }
        if (entityConstructors.size() != 1) {
            throw new ProcessingErrorException(List.of(new ProcessingError(entityConstructorError(type, "More than one public constructor is marked with @EntityConstructor.", "Keep @EntityConstructor on exactly one public constructor."), type)));
        }
        return entityConstructors.get(0);
    }

    private static String entityConstructorError(TypeElement type, String problem, String fix) {
        return """
            Database entity constructor is invalid:
              %s

            Problem:
              %s

            Hint:
              Entity mapper generation needs exactly one constructor to create the entity from database columns.

            Fix:
              %s
            """.formatted(type.getQualifiedName(), problem, fix);
    }

}
