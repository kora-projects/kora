package ru.tinkoff.kora.database.annotation.processor;

import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingError;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.common.naming.NameConverter;
import ru.tinkoff.kora.common.naming.SnakeCaseNameConverter;

import javax.annotation.Nullable;
import javax.lang.model.element.*;
import java.util.List;
import java.util.stream.Collectors;

public class EntityUtils {

    private final static SnakeCaseNameConverter defaultColumnNameConverter = new SnakeCaseNameConverter();

    public static String parseColumnName(VariableElement element, @Nullable NameConverter columnsNameConverter) {
        var column = AnnotationUtils.findAnnotation(element, DbUtils.COLUMN_ANNOTATION);
        var fieldName = element.getSimpleName().toString();
        if (column != null) {
            return AnnotationUtils.parseAnnotationValueWithoutDefault(column, "value");
        }
        return columnsNameConverter == null
            ? defaultColumnNameConverter.convert(fieldName)
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
            throw new ProcessingErrorException(List.of(new ProcessingError("Entity type " + type + " has no public constructors", type)));
        }
        if (constructors.size() == 1) {
            return constructors.get(0);
        }
        var entityConstructors = constructors.stream()
            .filter(c -> AnnotationUtils.findAnnotation(c, DbUtils.ENTITY_CONSTRUCTOR_ANNOTATION) != null)
            .toList();
        if (entityConstructors.isEmpty()) {
            throw new ProcessingErrorException(List.of(new ProcessingError("Entity type " + type + " has more than one public constructor and none of them is marked with @EntityConstructor", type)));
        }
        if (entityConstructors.size() != 1) {
            throw new ProcessingErrorException(List.of(new ProcessingError("Entity type " + type + " has more than one public constructor and more then one of them is marked with @EntityConstructor", type)));
        }
        return entityConstructors.get(0);
    }

}
