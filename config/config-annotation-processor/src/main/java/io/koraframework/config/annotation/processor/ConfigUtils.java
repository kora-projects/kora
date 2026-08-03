package io.koraframework.config.annotation.processor;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.TypeName;
import org.jspecify.annotations.Nullable;
import io.koraframework.annotation.processor.common.CommonUtils;
import io.koraframework.annotation.processor.common.CommonUtils.MappingData;
import io.koraframework.annotation.processor.common.Either;
import io.koraframework.annotation.processor.common.ProcessingError;

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
        TypeName.BOOLEAN, TypeName.BOOLEAN.box(),
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
            return Either.right(List.of(new ProcessingError(invalidConfigTypeError(typeElement), typeElement)));
        }
    }

    private static Either<List<ConfigField>, List<ProcessingError>> parseRecord(Types types, DeclaredType typeMirror, TypeElement te) {
        if (te.getKind() != ElementKind.RECORD) {
            throw new IllegalStateException(internalExpectedKindError(te, ElementKind.RECORD));
        }
        var fields = new ArrayList<ConfigField>();
        for (var recordComponent : te.getRecordComponents()) {
            var recordComponentType = types.asMemberOf(typeMirror, recordComponent);
            var name = recordComponent.getSimpleName().toString();
            var mapping = CommonUtils.parseMapping(recordComponent).getMapping(ConfigClassNames.configValueMapper);
            var isNullable = CommonUtils.isNullable(recordComponent) && !recordComponentType.getKind().isPrimitive();
            fields.add(new ConfigUtils.ConfigField(
                name, TypeName.get(recordComponentType), isNullable, false, mapping
            ));
        }
        return Either.left(fields);
    }

    private static Either<List<ConfigField>, List<ProcessingError>> parseInterface(Types types, DeclaredType typeMirror, TypeElement te) {
        if (te.getKind() != ElementKind.INTERFACE) {
            throw new IllegalStateException(internalExpectedKindError(te, ElementKind.INTERFACE));
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
            throw new IllegalStateException(internalExpectedKindError(te, ElementKind.INTERFACE));
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
                    errors.add(new ProcessingError(configMethodWithArgumentsError(method), method));
                }
            }
            if (method.getReturnType().getKind() == TypeKind.VOID) {
                if (method.getModifiers().contains(Modifier.DEFAULT)) {
                    continue;
                }
                errors.add(new ProcessingError(configVoidMethodError(method), method));
            }
            if (!method.getTypeParameters().isEmpty()) {
                errors.add(new ProcessingError(configGenericMethodError(method), method));
            }
            var methodType = (ExecutableType) types.asMemberOf(typeMirror, method);
            var name = method.getSimpleName().toString();
            if (seen.add(name)) {
                var isNullable = CommonUtils.isNullable(method) && !methodType.getReturnType().getKind().isPrimitive();
                var mapping = CommonUtils.parseMapping(method).getMapping(ConfigClassNames.configValueMapper);
                fields.add(new ConfigUtils.ConfigField(
                    name, TypeName.get(methodType.getReturnType()), isNullable, method.getModifiers().contains(Modifier.DEFAULT), mapping
                ));
            }
        }
        for (var superinterface : te.getInterfaces()) {
            var superinterfaceElement = (TypeElement) types.asElement(superinterface);
            parseInterface(types, (DeclaredType) superinterface, superinterfaceElement, fields, errors, seen);
        }
    }

    private static Either<List<ConfigField>, List<ProcessingError>> parseClass(Types types, DeclaredType typeMirror, TypeElement te) {
        var errors = new ArrayList<ProcessingError>();
        if (te.getKind() != ElementKind.CLASS) {
            throw new IllegalStateException(internalExpectedKindError(te, ElementKind.CLASS));
        }
        if (te.getModifiers().contains(Modifier.ABSTRACT)) {
            errors.add(new ProcessingError(configAbstractClassError(te), te));
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
                } else if (method.getParameters().isEmpty()) {
                    if (name.startsWith("get")) {
                        fieldsWithAccessors.computeIfAbsent(CommonUtils.decapitalize(name.substring(3)), n -> new FieldAndAccessors()).getter = method;
                    } else {
                        fieldsWithAccessors.computeIfAbsent(name, n -> new FieldAndAccessors()).getter = method;
                    }
                } else if (method.getParameters().size() == 1 && name.startsWith("set")) {
                    fieldsWithAccessors.computeIfAbsent(CommonUtils.decapitalize(name.substring(3)), n -> new FieldAndAccessors()).setter = method;
                }
            }
        }
        if (equals == null || hashCode == null) {
            errors.add(new ProcessingError(configEqualsHashCodeError(te), te));
            return Either.right(errors);
        }
        var constructors = CommonUtils.findConstructors(te, m -> m.contains(Modifier.PUBLIC));
        var emptyConstructor = constructors.stream().filter(e -> e.getParameters().isEmpty()).findFirst().orElse(null);
        var nonEmptyConstructor = constructors.stream().filter(e -> !e.getParameters().isEmpty()).findFirst().orElse(null);
        var constructorParams = nonEmptyConstructor == null ? Map.<String, VariableElement>of() : nonEmptyConstructor.getParameters().stream().collect(Collectors.toMap(
            p -> p.getSimpleName().toString(),
            p -> p
        ));

        var seen = new HashSet<String>();
        var fields = new ArrayList<ConfigField>();
        for (var fieldWithAccessors : fieldsWithAccessors.entrySet()) {
            var name = fieldWithAccessors.getKey();
            var value = fieldWithAccessors.getValue();
            if (value.getter == null || value.field == null) {
                continue;
            }
            if (value.setter == null && !constructorParams.containsKey(value.field.getSimpleName().toString())) {
                continue;
            }
            var fieldType = types.asMemberOf(typeMirror, value.field);
            if (seen.add(name)) {
                var isNullable = CommonUtils.isNullable(value.field) && !fieldType.getKind().isPrimitive();
                var mapping = CommonUtils.parseMapping(value.field).getMapping(ConfigClassNames.configValueMapper);
                var constructorParam = constructorParams.get(name);
                if (constructorParam != null) {
                    if (mapping == null) {
                        mapping = CommonUtils.parseMapping(constructorParam).getMapping(ConfigClassNames.configValueMapper);
                    }
                    isNullable = CommonUtils.isNullable(constructorParam) && !fieldType.getKind().isPrimitive();
                    ;
                }
                var hasDefault = emptyConstructor != null || !constructorParams.containsKey(value.field.getSimpleName().toString());
                fields.add(new ConfigUtils.ConfigField(
                    name, TypeName.get(fieldType), isNullable, hasDefault, mapping
                ));
            }
        }
        return Either.left(fields);
    }

    private static String invalidConfigTypeError(TypeElement typeElement) {
        return """
            Invalid config mapper target: `%s`.

            `@ConfigMapper` / `@ConfigSource` can be generated only for interfaces, classes, or records.
            Actual declaration kind: `%s`.

            Fix: move the annotation to a supported config DTO type.
            """.formatted(typeElement.getQualifiedName(), typeElement.getKind());
    }

    private static String configMethodWithArgumentsError(ExecutableElement method) {
        return """
            Invalid config interface method: `%s`.

            Non-default config methods must not have parameters because they describe config fields.

            Fix: remove method parameters, make the method `default`, or move this logic outside the config interface.
            """.formatted(method.getSimpleName());
    }

    private static String configVoidMethodError(ExecutableElement method) {
        return """
            Invalid config interface method: `%s`.

            Non-default config methods must return a value because they describe config fields.

            Fix: change the return type to the config field type, or make the method `default`.
            """.formatted(method.getSimpleName());
    }

    private static String configGenericMethodError(ExecutableElement method) {
        return """
            Invalid config interface method: `%s`.

            Config field methods cannot declare type parameters.

            Fix: use a concrete return type for this config field, or move generic helper logic to a default method.
            """.formatted(method.getSimpleName());
    }

    private static String configAbstractClassError(TypeElement typeElement) {
        return """
            Invalid config class: `%s`.

            Config classes must be instantiable, but this class is abstract.

            Fix: remove `abstract`, use an interface, or provide a concrete config DTO class.
            """.formatted(typeElement.getQualifiedName());
    }

    private static String configEqualsHashCodeError(TypeElement typeElement) {
        return """
            Invalid config class: `%s`.

            Config classes must override both `equals` and `hashCode` so generated config mapping can compare defaults and values reliably.

            Fix: implement `equals` and `hashCode`, use a record, or use an interface config declaration.
            """.formatted(typeElement.getQualifiedName());
    }

    private static String internalExpectedKindError(TypeElement typeElement, ElementKind expectedKind) {
        return """
            Kora internal error: config parser helper received `%s`, but expected `%s`.

            Actual declaration: `%s`
            Please report this with the annotated config type.
            """.formatted(typeElement.getKind(), expectedKind, typeElement.getQualifiedName());
    }

}
