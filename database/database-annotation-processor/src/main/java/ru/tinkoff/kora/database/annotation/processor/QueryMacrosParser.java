package ru.tinkoff.kora.database.annotation.processor;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.TypeName;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.MethodUtils;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.database.annotation.processor.jdbc.JdbcNativeType;
import ru.tinkoff.kora.database.annotation.processor.jdbc.JdbcNativeTypes;

import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.*;
import java.util.stream.Collectors;

final class QueryMacrosParser {

    private static final String MACROS_START = "%{";
    private static final String MACROS_END = "}";
    private static final String TARGET_RETURN = "return";
    private static final String SPECIAL_ID = "@id";

    private final Types types;

    QueryMacrosParser(Types types) {
        this.types = types;
    }

    record Field(Element field, String column, String path, boolean isId) {}

    record Target(DeclaredType type, String name) {}

    public String parse(String sqlWithSyntax, DeclaredType repositoryType, ExecutableElement method) {
        var sqlBuilder = new StringBuilder();
        var prevCmdIndex = 0;
        while (true) {
            var cmdIndexStart = sqlWithSyntax.indexOf(MACROS_START, prevCmdIndex);
            if (cmdIndexStart == -1) {
                return sqlBuilder.append(sqlWithSyntax.substring(prevCmdIndex)).toString();
            }

            var cmdIndexEnd = sqlWithSyntax.indexOf(MACROS_END, cmdIndexStart);
            var targetAndCmdAsStr = sqlWithSyntax.substring(cmdIndexStart + 2, cmdIndexEnd);

            var substitution = getSubstitution(targetAndCmdAsStr, repositoryType, method);
            sqlBuilder.append(sqlWithSyntax, prevCmdIndex, cmdIndexStart).append(substitution);

            prevCmdIndex = cmdIndexEnd + 1;
        }
    }

    private List<Field> getPathField(ExecutableElement method, DeclaredType target, String rootPath, String columnPrefix) {
        final JdbcNativeType nativeType = JdbcNativeTypes.findNativeType(TypeName.get(target));
        if (nativeType != null) {
            throw new ProcessingErrorException("Can't process argument '" + rootPath + "' as macros cause it is Native Type: " + target, method);
        }

        var result = new ArrayList<Field>();
        for (var field : getFields(target)) {
            var path = rootPath.isEmpty()
                ? field.getSimpleName().toString()
                : rootPath + "." + field.getSimpleName().toString();

            var isId = AnnotationUtils.isAnnotationPresent(field, DbUtils.ID_ANNOTATION);
            var embedded = AnnotationUtils.findAnnotation(field, DbUtils.EMBEDDED_ANNOTATION);
            if (embedded != null) {
                if (field.asType() instanceof DeclaredType dt) {
                    var prefix = Objects.requireNonNullElse(AnnotationUtils.parseAnnotationValueWithoutDefault(embedded, "value"), "");
                    for (var f : getPathField(method, dt, path, prefix)) {
                        result.add(new Field(f.field(), f.column(), f.path(), isId));
                    }
                } else {
                    throw new IllegalArgumentException("@Embedded annotation placed on field that can't be embedded: " + target);
                }
            } else {
                var columnName = getColumnName(target, field, columnPrefix);
                result.add(new Field(field, columnName, path, isId));
            }
        }
        return result;
    }

    private String getColumnName(DeclaredType target, Element field, String columnPrefix) {
        var column = AnnotationUtils.findAnnotation(field, DbUtils.COLUMN_ANNOTATION);
        var columnName = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(column, "value");
        if (columnName == null || columnName.isEmpty()) {
            var nameConverter = CommonUtils.getNameConverter(EntityUtils.SNAKE_CASE_NAME_CONVERTER, ((TypeElement) target.asElement()));
            return columnPrefix.isBlank()
                ? nameConverter.convert(field.getSimpleName().toString())
                : columnPrefix + nameConverter.convert(field.getSimpleName().toString());
        }
        return (columnPrefix.isBlank())
            ? columnName
            : columnPrefix + columnName;
    }

    private Set<String> getCommandSelectorPaths(DeclaredType type, String rootPath, String selects) {
        var result = new LinkedHashSet<String>();
        var fields = getFields(type);
        for (var fieldName : selects.strip().split(",")) {
            fieldName = fieldName.strip();
            var field = (Element) null;
            if (fieldName.equals(SPECIAL_ID)) {
                for (var f : fields)
                    if (AnnotationUtils.isAnnotationPresent(f, DbUtils.ID_ANNOTATION)) field = f;
                if (field == null)
                    throw new IllegalArgumentException("@Id annotated field not found, but was present in query marcos: " + selects.strip());
            } else {
                for (var f : fields)
                    if (f.getSimpleName().contentEquals(fieldName)) field = f;
                if (field == null)
                    throw new IllegalArgumentException("Field '" + fieldName + "' not found, but was present in query marcos: " + selects.strip());
            }
            var isEmbedded = AnnotationUtils.isAnnotationPresent(field, DbUtils.EMBEDDED_ANNOTATION);
            if (isEmbedded) {
                if (field.asType() instanceof DeclaredType dt) {
                    for (var embeddedField : getFields(dt)) {
                        result.add(rootPath + "." + field.getSimpleName() + "." + embeddedField.getSimpleName());
                    }
                } else {
                    throw new IllegalArgumentException("@Id @Embedded annotated illegal field in query marcos: " + selects.strip());
                }
            }
            result.add(rootPath + "." + field.getSimpleName());
        }
        return result;
    }

    private List<Element> getFields(DeclaredType type) {
        if (type.asElement().getKind() == ElementKind.RECORD) {
            return type.asElement().getEnclosedElements().stream()
                .filter(e -> e instanceof RecordComponentElement)
                .map(e -> ((Element) e))
                .toList();
        } else {
            return type.asElement().getEnclosedElements().stream()
                .filter(e -> e instanceof VariableElement)
                .map(e -> ((Element) e))
                .toList();
        }
    }

    private String getSubstitution(String targetAndCommand, DeclaredType repositoryType, ExecutableElement method) {
        try {
            var targetAndCmd = targetAndCommand.split("#");
            if (targetAndCmd.length == 1) {
                throw new ProcessingErrorException("Can't extract query marcos and target from: " + targetAndCommand, method);
            }

            var target = getTarget(targetAndCmd[0].strip(), repositoryType, method);
            var selectors = targetAndCmd[1].split("-=");
            final boolean include;
            if (selectors.length == 1) {
                include = true;
                selectors = targetAndCmd[1].split("=");
            } else {
                include = false;
            }

            var commandAsStr = selectors[0].strip().toLowerCase();

            var paths = (selectors.length != 1)
                ? getCommandSelectorPaths(target.type(), target.name(), selectors[1])
                : Set.<String>of();

            var fields = paths.isEmpty()
                ? getPathField(method, target.type(), target.name(), "")
                : getPathField(method, target.type(), target.name(), "").stream()
                .filter(f -> include == paths.contains(f.path()))
                .toList();

            var tableName = target.type().asElement().getAnnotationMirrors().stream()
                .filter(a -> DbUtils.TABLE_ANNOTATION.equals(ClassName.get(a.getAnnotationType())))
                .findFirst()
                .map(a -> AnnotationUtils.<String>parseAnnotationValueWithoutDefault(a, "value"))
                .orElseGet(() -> {
                    var nameConverter = Optional.ofNullable(CommonUtils.getNameConverter(((TypeElement) target.type().asElement())))
                        .orElseGet(() -> EntityUtils.SNAKE_CASE_NAME_CONVERTER);

                    return nameConverter.convert(target.type().asElement().getSimpleName().toString());
                });
            return switch (commandAsStr) {
                case "table" -> tableName;
                case "selects" -> fields.stream()
                    .map(Field::column)
                    .collect(Collectors.joining(", "));
                case "inserts" -> {
                    var tableAndColumnPrefix = fields.stream()
                        .map(Field::column)
                        .collect(Collectors.joining(", ", tableName + "(", ")"));

                    var inserts = fields.stream()
                        .map(f -> ":" + f.path())
                        .collect(Collectors.joining(", ", "VALUES (", ")"));

                    yield tableAndColumnPrefix + " " + inserts;
                }
                case "updates" -> fields.stream()
                    .filter(f -> !f.isId())
                    .map(f -> f.column() + " = :" + f.path())
                    .collect(Collectors.joining(", "));
                case "where" -> fields.stream()
                    .map(f -> f.column() + " = :" + f.path())
                    .collect(Collectors.joining(" AND "));
                default -> throw new ProcessingErrorException("Unknown query marcos specified: " + targetAndCommand, method);
            };

        } catch (IllegalArgumentException e) {
            throw new ProcessingErrorException(e.getMessage(), method);
        }
    }


    private Target getTarget(String targetName, DeclaredType repositoryType, ExecutableElement method) {
        var methodType = (ExecutableType) this.types.asMemberOf(repositoryType, method);

        TypeMirror targetMirror = null;
        if (TARGET_RETURN.equals(targetName)) {
            if (MethodUtils.isVoid(method)) {
                throw new ProcessingErrorException("Macros command specified 'return' target, but return value is type Void", method);
            } else if (method.getReturnType().toString().equals(DbUtils.UPDATE_COUNT.canonicalName())) {
                throw new ProcessingErrorException("Macros command specified 'return' target, but return value is type UpdateCount", method);
            }

            if (CommonUtils.isFuture(methodType.getReturnType())
                || CommonUtils.isMono(methodType.getReturnType())
                || CommonUtils.isFlux(methodType.getReturnType())
                || CommonUtils.isOptional(methodType.getReturnType())
                || CommonUtils.isCollection(methodType.getReturnType())) {
                targetMirror = MethodUtils.getGenericType(methodType.getReturnType()).orElseThrow();
                if (CommonUtils.isOptional(targetMirror) || CommonUtils.isCollection(targetMirror)) {
                    targetMirror = MethodUtils.getGenericType(targetMirror).orElseThrow();
                }
            } else {
                targetMirror = methodType.getReturnType();
            }
        } else {
            var parameters = method.getParameters();
            for (int i = 0; i < parameters.size(); i++) {
                var parameter = parameters.get(i);
                if (parameter.getSimpleName().contentEquals(targetName)) {
                    targetMirror = methodType.getParameterTypes().get(i);
                }
            }

            if (targetMirror == null) {
                throw new ProcessingErrorException("Macros command unspecified target received: " + targetName, method);
            }

            if (CommonUtils.isCollection(targetMirror) || CommonUtils.isOptional(targetMirror)) {
                targetMirror = MethodUtils.getGenericType(targetMirror).orElseThrow();
                if (CommonUtils.isOptional(targetMirror) || CommonUtils.isCollection(targetMirror)) {
                    targetMirror = MethodUtils.getGenericType(targetMirror).orElseThrow();
                }
            }
        }

        if (targetMirror instanceof DeclaredType dt) {
            return new Target(dt, targetName);
        } else {
            throw new ProcessingErrorException("Macros command unprocessable target type: " + targetName, method);
        }
    }
}
