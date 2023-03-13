package ru.tinkoff.kora.validation.annotation.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public record ValidMeta(Type source, Validator validator, TypeElement sourceElement, List<Field> fields) {

    public static final ClassName VALID_TYPE = ClassName.get("ru.tinkoff.kora.validation.common.annotation", "Valid");
    public static final ClassName VALIDATED_BY_TYPE = ClassName.get("ru.tinkoff.kora.validation.common.annotation", "ValidatedBy");
    public static final ClassName CONTEXT_TYPE = ClassName.get("ru.tinkoff.kora.validation.common", "ValidationContext");
    public static final ClassName VALIDATOR_TYPE = ClassName.get("ru.tinkoff.kora.validation.common", "Validator");
    public static final ClassName VIOLATION_TYPE = ClassName.get("ru.tinkoff.kora.validation.common", "Violation");
    public static final ClassName EXCEPTION_TYPE = ClassName.get("ru.tinkoff.kora.validation.common", "ViolationException");

    public ValidMeta(Type source, TypeElement sourceElement, List<Field> fields) {
        this(source,
            new Validator(
                Type.ofName(VALIDATOR_TYPE.canonicalName(), List.of(source)),
                new Type(source.packageName, "$" + source.simpleName() + "_Validator", List.of(source))
            ),
            sourceElement, fields);
    }

    public record Validated(Type target) {

        public Type validator() {
            return Type.ofName(VALIDATOR_TYPE.canonicalName(), List.of(target));
        }
    }

    public record Validator(Type contract, Type implementation) {}

    public record Field(Type type, String name, boolean isRecord, boolean isNullable, boolean isPrimitive, List<Constraint> constraint, List<Validated> validates) {

        public boolean isNotNull() {
            return !isNullable;
        }

        public String accessor() {
            return (isRecord)
                ? name + "()"
                : "get" + name.substring(0, 1).toUpperCase() + name.substring(1) + "()";
        }
    }

    public record Constraint(Type annotation, Factory factory) {

        public record Factory(Type type, Map<String, Object> parameters) {

            public Type validator() {
                return Type.ofName(VALIDATOR_TYPE.canonicalName(), type.generic());
            }
        }
    }

    public record Type(String packageName, String simpleName, List<Type> generic) {

        public static Type ofType(TypeMirror type) {
            if (type instanceof DeclaredType) {
                return ofType(((DeclaredType) type));
            }

            return ofName(type.toString());
        }

        public static Type ofType(DeclaredType type) {
            final List<Type> generics = type.getTypeArguments().stream()
                .map(Type::ofType)
                .toList();

            return ofName(type.asElement().toString(), generics);
        }

        public static Type ofClass(Class<?> clazz) {
            return ofClass(clazz, Collections.emptyList());
        }

        public static Type ofClass(Class<?> clazz, List<Type> generic) {
            return new Type(clazz.getPackageName(), clazz.getSimpleName(), generic);
        }

        public static Type ofName(String canonicalName) {
            return ofName(canonicalName, Collections.emptyList());
        }

        public static Type ofName(String canonicalName, List<Type> generic) {
            return new Type(canonicalName.substring(0, canonicalName.lastIndexOf('.')),
                canonicalName.substring(canonicalName.lastIndexOf('.') + 1),
                generic);
        }

        public String canonicalName() {
            return packageName + "." + simpleName;
        }

        public TypeMirror asMirror(ProcessingEnvironment env) {
            if (generic.isEmpty()) {
                return env.getTypeUtils().getDeclaredType(env.getElementUtils().getTypeElement(canonicalName()));
            } else {
                final TypeMirror[] generics = generic().stream()
                    .map(g -> g.asMirror(env))
                    .toArray(TypeMirror[]::new);

                return env.getTypeUtils().getDeclaredType(env.getElementUtils().getTypeElement(canonicalName()), generics);
            }
        }

        public TypeName asPoetType(ProcessingEnvironment env) {
            return (generic.isEmpty())
                ? TypeName.get(asMirror(env))
                : ParameterizedTypeName.get(asMirror(env));
        }

        @Override
        public String toString() {
            if (generic().isEmpty()) {
                return canonicalName();
            }

            final String generics = generic().stream()
                .map(Type::toString)
                .collect(Collectors.joining(", ", "<", ">"));

            return canonicalName() + generics;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Type)) return false;
            Type type = (Type) o;
            return Objects.equals(packageName, type.packageName) && Objects.equals(simpleName, type.simpleName) && Objects.equals(generic, type.generic);
        }

        @Override
        public int hashCode() {
            return Objects.hash(packageName, simpleName, generic);
        }
    }
}
