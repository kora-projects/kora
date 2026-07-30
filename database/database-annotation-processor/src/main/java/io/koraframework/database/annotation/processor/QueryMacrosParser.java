package io.koraframework.database.annotation.processor;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.TypeName;
import io.koraframework.annotation.processor.common.AnnotationUtils;
import io.koraframework.annotation.processor.common.CommonUtils;
import io.koraframework.annotation.processor.common.MethodUtils;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import io.koraframework.database.annotation.processor.jdbc.JdbcNativeType;
import io.koraframework.database.annotation.processor.jdbc.JdbcNativeTypes;

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

    record Field(Element field, String column, String rawColumn, String path, String targetPath, boolean isId) {}

    record Target(DeclaredType type, String name, String column, String columnPrefix) {}

    record Command(String target, String command, String alias) {}

    public String parse(String sqlWithSyntax, DeclaredType repositoryType, ExecutableElement method) {
        var aliases = collectAliases(sqlWithSyntax, repositoryType, method);
        var sqlBuilder = new StringBuilder();
        var prevCmdIndex = 0;
        while (true) {
            var cmdIndexStart = sqlWithSyntax.indexOf(MACROS_START, prevCmdIndex);
            if (cmdIndexStart == -1) {
                return sqlBuilder.append(sqlWithSyntax.substring(prevCmdIndex)).toString();
            }

            var cmdIndexEnd = sqlWithSyntax.indexOf(MACROS_END, cmdIndexStart);
            var targetAndCmdAsStr = sqlWithSyntax.substring(cmdIndexStart + 2, cmdIndexEnd);

            var substitution = getSubstitution(targetAndCmdAsStr, repositoryType, method, aliases);
            sqlBuilder.append(sqlWithSyntax, prevCmdIndex, cmdIndexStart).append(substitution);

            prevCmdIndex = cmdIndexEnd + 1;
        }
    }

    private Map<String, String> collectAliases(String sqlWithSyntax, DeclaredType repositoryType, ExecutableElement method) {
        var aliases = new HashMap<String, String>();
        var prevCmdIndex = 0;
        while (true) {
            var cmdIndexStart = sqlWithSyntax.indexOf(MACROS_START, prevCmdIndex);
            if (cmdIndexStart == -1) {
                return aliases;
            }

            var cmdIndexEnd = sqlWithSyntax.indexOf(MACROS_END, cmdIndexStart);
            var targetAndCmdAsStr = sqlWithSyntax.substring(cmdIndexStart + 2, cmdIndexEnd);
            var command = parseCommand(targetAndCmdAsStr, method);
            if ("table".equals(command.command()) && command.alias() != null) {
                getTarget(command.target(), repositoryType, method);
                aliases.put(command.target(), command.alias());
            }

            prevCmdIndex = cmdIndexEnd + 1;
        }
    }

    private Command parseCommand(String targetAndCommand, ExecutableElement method) {
        var targetAndCmd = targetAndCommand.split("#", 2);
        if (targetAndCmd.length == 1) {
            throw new ProcessingErrorException("Can't extract query marcos and target from: " + targetAndCommand, method);
        }

        var target = targetAndCmd[0].strip();
        var selectors = targetAndCmd[1].split("-=", 2);
        if (selectors.length == 1) {
            selectors = targetAndCmd[1].split("=", 2);
        }

        var commandAsStr = selectors[0].strip();
        var lowerCommand = commandAsStr.toLowerCase(Locale.ROOT);
        if (lowerCommand.startsWith("table as ")) {
            var alias = commandAsStr.substring("table as ".length()).strip();
            if (alias.isEmpty()) {
                throw new ProcessingErrorException("Table alias is empty in query marcos: " + targetAndCommand, method);
            }
            return new Command(target, "table", alias);
        }
        return new Command(target, lowerCommand, null);
    }

    private List<Field> getPathField(ExecutableElement method, DeclaredType target, String rootPath, String rootTargetPath, String columnPrefix) {
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
                    if (CommonUtils.isCollection(dt)) {
                        dt = (DeclaredType) MethodUtils.getGenericType(dt).orElseThrow();
                    }
                    var prefix = Objects.requireNonNullElse(AnnotationUtils.parseAnnotationValueWithoutDefault(embedded, "value"), "");
                    var targetPath = rootTargetPath.isEmpty()
                        ? field.getSimpleName().toString()
                        : rootTargetPath + "." + field.getSimpleName().toString();
                    for (var f : getPathField(method, dt, path, targetPath, columnPrefix + prefix)) {
                        result.add(new Field(f.field(), f.column(), f.rawColumn(), f.path(), f.targetPath(), isId || f.isId()));
                    }
                } else {
                    throw new IllegalArgumentException("@Embedded annotation placed on field that can't be embedded: " + target);
                }
            } else {
                var rawColumnName = getColumnName(target, field);
                var columnName = columnPrefix.isBlank() ? rawColumnName : columnPrefix + rawColumnName;
                result.add(new Field(field, columnName, rawColumnName, path, rootTargetPath, isId));
            }
        }
        return result;
    }

    private String getColumnName(DeclaredType target, Element field) {
        var column = AnnotationUtils.findAnnotation(field, DbUtils.COLUMN_ANNOTATION);
        var columnName = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(column, "value");
        if (columnName == null || columnName.isEmpty()) {
            var nameConverter = CommonUtils.getNameConverter(EntityUtils.SNAKE_CASE_NAME_CONVERTER, ((TypeElement) target.asElement()));
            return nameConverter.convert(field.getSimpleName().toString());
        }
        return columnName;
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

    private List<Field> getFields(ExecutableElement method, Target target) {
        final JdbcNativeType nativeType = JdbcNativeTypes.findNativeType(TypeName.get(target.type()));
        if (nativeType != null && target.column() != null) {
            return List.of(new Field(null, target.column(), target.column(), target.name(), target.name(), false));
        }

        return getPathField(method, target.type(), target.name(), target.name(), target.columnPrefix());
    }

    private String getSubstitution(String targetAndCommand, DeclaredType repositoryType, ExecutableElement method, Map<String, String> aliases) {
        try {
            var targetAndCmd = targetAndCommand.split("#", 2);
            if (targetAndCmd.length == 1) {
                throw new ProcessingErrorException("Can't extract query marcos and target from: " + targetAndCommand, method);
            }

            var target = getTarget(targetAndCmd[0].strip(), repositoryType, method);
            var command = parseCommand(targetAndCommand, method);
            var selectors = targetAndCmd[1].split("-=", 2);
            final boolean include;
            if (selectors.length == 1) {
                include = true;
                selectors = targetAndCmd[1].split("=", 2);
            } else {
                include = false;
            }

            var commandAsStr = command.command();

            var paths = (selectors.length != 1)
                ? getCommandSelectorPaths(target.type(), target.name(), selectors[1])
                : Set.<String>of();

            var fields = paths.isEmpty()
                ? getFields(method, target)
                : getFields(method, target).stream()
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
                case "table" -> command.alias() == null
                    ? tableName
                    : tableName + " " + command.alias();
                case "selects" -> fields.stream()
                    .map(f -> selectExpression(f, aliases))
                    .collect(Collectors.joining(", "));
                case "columns" -> fields.stream()
                    .map(Field::column)
                    .collect(Collectors.joining(", "));
                case "values" -> fields.stream()
                    .map(f -> ":" + f.path())
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
                    .map(f -> columnReference(f, aliases) + " = :" + f.path())
                    .collect(Collectors.joining(" AND "));
                default -> throw new ProcessingErrorException("Unknown query marcos specified: " + targetAndCommand, method);
            };

        } catch (IllegalArgumentException e) {
            throw new ProcessingErrorException(e.getMessage(), method);
        }
    }

    private String selectExpression(Field field, Map<String, String> aliases) {
        var reference = columnReference(field, aliases);
        if (aliases.containsKey(field.targetPath()) && !field.column().equals(field.rawColumn())) {
            return reference + " AS " + field.column();
        }
        return reference;
    }

    private String columnReference(Field field, Map<String, String> aliases) {
        var alias = aliases.get(field.targetPath());
        if (alias == null) {
            return field.column();
        }
        return alias + "." + field.rawColumn();
    }

    private Target getTarget(String targetName, DeclaredType repositoryType, ExecutableElement method) {
        var path = Arrays.stream(targetName.split("\\."))
            .map(String::strip)
            .filter(s -> !s.isBlank())
            .toList();
        if (path.isEmpty()) {
            throw new ProcessingErrorException("Macros command unspecified target received: " + targetName, method);
        }

        var methodType = (ExecutableType) this.types.asMemberOf(repositoryType, method);

        TypeMirror targetMirror = null;
        var rootTargetName = path.get(0);
        if (TARGET_RETURN.equals(rootTargetName)) {
            if (MethodUtils.isVoid(method)) {
                throw new ProcessingErrorException("Macros command specified 'return' target, but return value is type Void", method);
            } else if (method.getReturnType().toString().equals(DbUtils.UPDATE_COUNT.canonicalName())) {
                throw new ProcessingErrorException("Macros command specified 'return' target, but return value is type UpdateCount", method);
            }

            if (CommonUtils.isFuture(methodType.getReturnType())
                || CommonUtils.isCompletionStage(methodType.getReturnType())
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
                if (parameter.getSimpleName().contentEquals(rootTargetName)) {
                    targetMirror = methodType.getParameterTypes().get(i);
                }
            }

            if (targetMirror == null) {
                var genericTarget = getTypeParameterTarget(repositoryType, method, rootTargetName);
                if (genericTarget != null) {
                    return genericTarget;
                }
                throw new ProcessingErrorException("Macros command unspecified target received: " + rootTargetName, method);
            }

            if (CommonUtils.isCollection(targetMirror) || CommonUtils.isOptional(targetMirror)) {
                targetMirror = MethodUtils.getGenericType(targetMirror).orElseThrow();
                if (CommonUtils.isOptional(targetMirror) || CommonUtils.isCollection(targetMirror)) {
                    targetMirror = MethodUtils.getGenericType(targetMirror).orElseThrow();
                }
            }
        }

        if (targetMirror instanceof DeclaredType dt) {
            var target = new Target(dt, rootTargetName, getColumnName(method, rootTargetName, targetMirror), "");
            for (int i = 1; i < path.size(); i++) {
                target = getChildTarget(method, target, path.get(i));
            }
            return target;
        } else {
            throw new ProcessingErrorException("Macros command unprocessable target type: " + targetName, method);
        }
    }

    private Target getChildTarget(ExecutableElement method, Target target, String childName) {
        for (var field : getFields(target.type())) {
            if (!field.getSimpleName().contentEquals(childName)) {
                continue;
            }
            if (field.asType() instanceof DeclaredType dt) {
                if (CommonUtils.isCollection(dt)) {
                    dt = (DeclaredType) MethodUtils.getGenericType(dt).orElseThrow();
                }
                var embedded = AnnotationUtils.findAnnotation(field, DbUtils.EMBEDDED_ANNOTATION);
                var prefix = embedded == null
                    ? ""
                    : Objects.requireNonNullElse(AnnotationUtils.parseAnnotationValueWithoutDefault(embedded, "value"), "");
                return new Target(dt, target.name() + "." + childName, null, target.columnPrefix() + prefix);
            }
            throw new ProcessingErrorException("Macros command unprocessable target type: " + target.name() + "." + childName, method);
        }
        throw new ProcessingErrorException("Field '" + childName + "' not found, but was present in query marcos target: " + target.name(), method);
    }

    private Target getTypeParameterTarget(DeclaredType repositoryType, ExecutableElement method, String targetName) {
        var methodType = (ExecutableType) this.types.asMemberOf(repositoryType, method);
        var parameters = method.getParameters();
        for (int i = 0; i < parameters.size(); i++) {
            var parameter = parameters.get(i);
            if (parameter.asType() instanceof javax.lang.model.type.TypeVariable typeVariable
                && typeVariable.asElement().getSimpleName().contentEquals(targetName)) {
                var type = methodType.getParameterTypes().get(i);
                if (type instanceof DeclaredType dt) {
                    return new Target(dt, parameter.getSimpleName().toString(), getColumnName(method, targetName, type), "");
                }
                throw new ProcessingErrorException("Macros command unprocessable target type: " + targetName, method);
            }
        }

        if (method.getEnclosingElement() instanceof TypeElement enclosingType) {
            for (var typeParameter : enclosingType.getTypeParameters()) {
                if (typeParameter.getSimpleName().contentEquals(targetName)) {
                    var type = this.types.asMemberOf(repositoryType, typeParameter);
                    if (type instanceof DeclaredType dt) {
                        return new Target(dt, targetName, getColumnName(method, targetName, type), "");
                    }
                    throw new ProcessingErrorException("Macros command unprocessable target type: " + targetName, method);
                }
            }
        }

        for (var typeParameter : method.getTypeParameters()) {
            if (typeParameter.getSimpleName().contentEquals(targetName)) {
                var type = this.types.asMemberOf(repositoryType, typeParameter);
                if (type instanceof DeclaredType dt) {
                    return new Target(dt, targetName, getColumnName(method, targetName, type), "");
                }
                throw new ProcessingErrorException("Macros command unprocessable target type: " + targetName, method);
            }
        }

        return null;
    }

    private String getColumnName(ExecutableElement method, String targetName, TypeMirror targetMirror) {
        if (!TARGET_RETURN.equals(targetName)) {
            for (var parameter : method.getParameters()) {
                if (parameter.getSimpleName().contentEquals(targetName)) {
                    var column = AnnotationUtils.findAnnotation(parameter, DbUtils.COLUMN_ANNOTATION);
                    var columnName = AnnotationUtils.<String>parseAnnotationValueWithoutDefault(column, "value");
                    if (columnName != null && !columnName.isEmpty()) {
                        return columnName;
                    }
                }
            }
        }

        return targetMirror.getAnnotationMirrors().stream()
            .filter(a -> DbUtils.COLUMN_ANNOTATION.equals(ClassName.get(a.getAnnotationType())))
            .findFirst()
            .map(a -> AnnotationUtils.<String>parseAnnotationValueWithoutDefault(a, "value"))
            .filter(columnName -> !columnName.isEmpty())
            .orElse(null);
    }
}
