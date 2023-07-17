package ru.tinkoff.kora.config.annotation.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils;
import ru.tinkoff.kora.annotation.processor.common.CommonUtils.MappingData;
import ru.tinkoff.kora.annotation.processor.common.ProcessingError;
import ru.tinkoff.kora.common.util.Either;

import javax.annotation.Nullable;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Types;
import java.util.*;
import java.util.stream.Collectors;

public class ConfigUtils {
    public static final Set<TypeName> SUPPORTED_TYPES = Set.of(
        TypeName.INT, TypeName.INT.box(),
        TypeName.LONG, TypeName.LONG.box(),
        TypeName.DOUBLE, TypeName.DOUBLE.box(),
        ClassName.get(String.class)
    );

    public static boolean isSupportedType(TypeName typeName) {
        return SUPPORTED_TYPES.contains(typeName);
    }

    public record ConfigField(String name, TypeName typeName, boolean isNullable, boolean hasDefault, @Nullable MappingData mapping) {}

    public static Either<List<ConfigField>, List<ProcessingError>> parseFields(Types types, TypeElement typeElement) {
        var type = (DeclaredType) typeElement.asType();
        if (typeElement.getKind() == ElementKind.RECORD) {
            return parseRecord(types, type, typeElement);
        } else if (typeElement.getKind() == ElementKind.INTERFACE) {
            return parseInterface(types, type, typeElement);
        } else if (typeElement.getKind() == ElementKind.CLASS) {
            return parseClass(types, type, typeElement);
        } else {
            return Either.right(List.of(new ProcessingError("typeElement should be interface, class or record, got " + typeElement.getKind(), typeElement)));
        }
    }

    private static Either<List<ConfigField>, List<ProcessingError>> parseRecord(Types types, DeclaredType typeMirror, TypeElement te) {
        if (te.getKind() != ElementKind.RECORD) {
            throw new IllegalArgumentException("Method expecting record");
        }
        var fields = new ArrayList<ConfigField>();
        for (var recordComponent : te.getRecordComponents()) {
            var recordComponentType = types.asMemberOf(typeMirror, recordComponent);
            var name = recordComponent.getSimpleName().toString();
            var mapping = CommonUtils.parseMapping(recordComponent).getMapping(ConfigClassNames.configValueExtractor);
            var isNullable = CommonUtils.isNullable(recordComponent) && !recordComponentType.getKind().isPrimitive();
            fields.add(new ConfigUtils.ConfigField(
                name, TypeName.get(recordComponentType), isNullable, false, mapping
            ));
        }
        return Either.left(fields);
    }

    private static Either<List<ConfigField>, List<ProcessingError>> parseInterface(Types types, DeclaredType typeMirror, TypeElement te) {
        if (te.getKind() != ElementKind.INTERFACE) {
            throw new IllegalArgumentException("Method expecting interface");
        }
        var seen = new HashSet<String>();
        var errors = new ArrayList<ProcessingError>();
        var fields = new ArrayList<ConfigField>();

        parseInterface(types, typeMirror, te, fields, errors, seen);
        if (errors.isEmpty()) {
            return Either.left(fields);
        } else {
            return Either.right(errors);
        }
    }

    private static void parseInterface(Types types, DeclaredType typeMirror, TypeElement te, List<ConfigField> fields, List<ProcessingError> errors, Set<String> seen) {
        if (te.getKind() != ElementKind.INTERFACE) {
            throw new IllegalArgumentException("Method expecting interface");
        }
        for (var enclosedElement : te.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.METHOD || enclosedElement.getModifiers().contains(Modifier.STATIC) || enclosedElement.getModifiers().contains(Modifier.PRIVATE)) {
                continue;
            }
            var method = (ExecutableElement) enclosedElement;
            if (!method.getParameters().isEmpty()) {
                if (method.getModifiers().contains(Modifier.DEFAULT)) {
                    continue;
                } else {
                    errors.add(new ProcessingError("Config has non default method with arguments", method));
                }
            }
            if (method.getReturnType().getKind() == TypeKind.VOID) {
                if (method.getModifiers().contains(Modifier.DEFAULT)) {
                    continue;
                }
                errors.add(new ProcessingError("Config has non default method returning void", method));
            }
            if (!method.getTypeParameters().isEmpty()) {
                errors.add(new ProcessingError("Config has method with type parameters", method));
            }
            var methodType = (ExecutableType) types.asMemberOf(typeMirror, method);
            var name = method.getSimpleName().toString();
            if (seen.add(name)) {
                var isNullable = CommonUtils.isNullable(method) && !methodType.getReturnType().getKind().isPrimitive();
                var mapping = CommonUtils.parseMapping(method).getMapping(ConfigClassNames.configValueExtractor);
                fields.add(new ConfigUtils.ConfigField(
                    name, TypeName.get(methodType.getReturnType()), isNullable, method.getModifiers().contains(Modifier.DEFAULT), mapping
                ));
            }
            for (var superinterface : te.getInterfaces()) {
                var superinterfaceElement = (TypeElement) types.asElement(superinterface);
                parseInterface(types, (DeclaredType) superinterface, superinterfaceElement, fields, errors, seen);
            }
        }
    }

    private static Either<List<ConfigField>, List<ProcessingError>> parseClass(Types types, DeclaredType typeMirror, TypeElement te) {
        var errors = new ArrayList<ProcessingError>();
        if (te.getKind() != ElementKind.CLASS) {
            throw new IllegalArgumentException("Method expecting record");
        }
        if (te.getModifiers().contains(Modifier.ABSTRACT)) {
            errors.add(new ProcessingError("Config annotated class can't be abstract", te));
            return Either.right(errors);
        }
        ExecutableElement equals = null;
        ExecutableElement hashCode = null;
        class FieldAndAccessors {
            VariableElement field;
            ExecutableElement getter;
            ExecutableElement setter;
        }
        var fieldsWithAccessors = new HashMap<String, FieldAndAccessors>();
        for (var enclosedElement : te.getEnclosedElements()) {
            var name = enclosedElement.getSimpleName().toString();
            if (enclosedElement.getKind() == ElementKind.FIELD) {
                fieldsWithAccessors.computeIfAbsent(name, n -> new FieldAndAccessors()).field = (VariableElement) enclosedElement;
            }
            if (enclosedElement.getKind() == ElementKind.METHOD) {
                if (enclosedElement.getModifiers().contains(Modifier.PRIVATE)) {
                    continue;
                }
                if (enclosedElement.getModifiers().contains(Modifier.STATIC)) {
                    continue;
                }
                var method = (ExecutableElement) enclosedElement;
                if (name.equals("equals") && method.getParameters().size() == 1) {
                    equals = method;
                } else if (name.equals("hashCode") && method.getParameters().isEmpty()) {
                    hashCode = method;
                } else {
                    if (name.startsWith("get")) {
                        fieldsWithAccessors.computeIfAbsent(CommonUtils.decapitalize(name.substring(3)), n -> new FieldAndAccessors()).getter = method;
                    } else if (name.startsWith("set")) {
                        fieldsWithAccessors.computeIfAbsent(CommonUtils.decapitalize(name.substring(3)), n -> new FieldAndAccessors()).setter = method;
                    }
                }
            }
        }
        if (equals == null || hashCode == null) {
            errors.add(new ProcessingError("Config annotated class must override equals and hashCode methods", te));
            return Either.right(errors);
        }
        var constructors = CommonUtils.findConstructors(te, m -> m.contains(Modifier.PUBLIC));
        var emptyConstructor = constructors.stream().filter(e -> e.getParameters().isEmpty()).findFirst().orElse(null);
        var nonEmptyConstructor = constructors.stream().filter(e -> !e.getParameters().isEmpty()).findFirst().orElse(null);
        var constructorParams = nonEmptyConstructor == null ? Set.of() : nonEmptyConstructor.getParameters().stream().map(VariableElement::getSimpleName).map(Objects::toString).collect(Collectors.toSet());

        var seen = new HashSet<String>();
        var fields = new ArrayList<ConfigField>();
        for (var fieldWithAccessors : fieldsWithAccessors.entrySet()) {
            var name = fieldWithAccessors.getKey();
            var value = fieldWithAccessors.getValue();
            if (value.getter == null) {
                continue;
            }
            if (value.setter == null && !constructorParams.contains(value.field.getSimpleName().toString())) {
                continue;
            }
            var fieldType = types.asMemberOf(typeMirror, value.field);
            if (seen.add(name)) {
                var isNullable = CommonUtils.isNullable(value.field) && !fieldType.getKind().isPrimitive();
                var mapping = CommonUtils.parseMapping(value.field).getMapping(ConfigClassNames.configValueExtractor);
                var hasDefault = emptyConstructor != null || !constructorParams.contains(value.field.getSimpleName().toString());
                fields.add(new ConfigUtils.ConfigField(
                    name, TypeName.get(fieldType), isNullable, hasDefault, mapping
                ));
            }
        }
        return Either.left(fields);
    }

}
