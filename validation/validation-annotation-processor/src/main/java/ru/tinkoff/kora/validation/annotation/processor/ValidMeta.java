package ru.tinkoff.kora.validation.annotation.processor;

import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import ru.tinkoff.kora.annotation.processor.common.NameUtils;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static ru.tinkoff.kora.validation.annotation.processor.ValidTypes.VALIDATOR_TYPE;

public record ValidMeta(Type source, TypeElement sourceElement, List<Field> fields) {

    public String validatorImplementationName() {
        return NameUtils.generatedType(sourceElement, VALIDATOR_TYPE);
    }

    public Type validator(ProcessingEnvironment env) {
        TypeElement validatorElement = env.getElementUtils().getTypeElement(VALIDATOR_TYPE.canonicalName());
        DeclaredType declaredType = env.getTypeUtils().getDeclaredType(validatorElement, source.typeMirror);
        return new Type(declaredType.asElement(), declaredType);
    }

    public ValidMeta(TypeElement sourceElement, List<Field> fields) {
        this(Type.ofElement(sourceElement, sourceElement.asType()), sourceElement, fields);
    }

    public record Validated(Type target) {

        public Type validator(ProcessingEnvironment env) {
            TypeElement validatorElement = env.getElementUtils().getTypeElement(VALIDATOR_TYPE.canonicalName());
            DeclaredType declaredType = env.getTypeUtils().getDeclaredType(validatorElement, target.typeMirror);
            return new Type(declaredType.asElement(), declaredType);
        }
    }

    public record Field(Type type, String name, boolean isRecord, boolean isNullable, boolean isNotNull,
                        boolean isJsonNullable, boolean isPrimitive, List<Constraint> constraint,
                        List<Validated> validates) {

        public String accessor() {
            return (isRecord)
                ? name + "()"
                : "get" + name.substring(0, 1).toUpperCase() + name.substring(1) + "()";
        }

        public String valueAccessor() {
            if (isJsonNullable) {
                return accessor() + ".value()";
            } else {
                return accessor();
            }
        }
    }

    public record Constraint(Type annotation, Factory factory) {

        public record Factory(Type type, Type validator, Map<String, Object> parameters) {

        }
    }

    public record Type(Element element, TypeMirror typeMirror) {

        public static Type ofElement(Element element, TypeMirror typeMirror) {
            return new Type(element, typeMirror);
        }

        public TypeName asPoetType() {
            return TypeName.get(typeMirror);
        }

        @Override
        public String toString() {
            return typeMirror.toString();
        }
    }
}
