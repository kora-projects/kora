package io.koraframework.validation.annotation.processor;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.TypeName;
import io.koraframework.annotation.processor.common.NameUtils;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.List;
import java.util.Map;

import static io.koraframework.validation.annotation.processor.ValidTypes.VALIDATOR_TYPE;

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

    /**
     * A container whose type argument carries validation annotations, like {@code List<@Valid Item>} or {@code Map<String, @Size(max = 5) String>}
     */
    public enum Container {
        ITERABLE("iterable"),
        MAP_KEYS("mapKeys"),
        MAP_VALUES("mapValues");

        private static final ClassName CONTAINER_VALIDATORS = ClassName.get("io.koraframework.validation.common.constraint", "ContainerValidators");

        private final String factoryMethod;

        Container(String factoryMethod) {
            this.factoryMethod = factoryMethod;
        }

        /**
         * @param containers from the outermost container to the innermost one
         * @return validator of the outermost container that applies the element validator to the innermost elements
         */
        public static CodeBlock wrap(List<Container> containers, CodeBlock elementValidator) {
            var result = elementValidator;
            for (int i = containers.size() - 1; i >= 0; i--) {
                result = CodeBlock.of("$T.$L($L)", CONTAINER_VALIDATORS, containers.get(i).factoryMethod, result);
            }
            return result;
        }
    }

    /**
     * @param target     type the validator is requested for
     * @param root       type of the validated field, differs from the target when the target is a type argument of a container
     * @param containers containers between the root and the target
     */
    public record Validated(Type target, Type root, List<Container> containers) {

        public Validated(Type target) {
            this(target, target, List.of());
        }

        public Type validator(ProcessingEnvironment env) {
            return validatorOf(env, target);
        }

        public Type rootValidator(ProcessingEnvironment env) {
            return validatorOf(env, root);
        }

        private static Type validatorOf(ProcessingEnvironment env, Type type) {
            TypeElement validatorElement = env.getElementUtils().getTypeElement(VALIDATOR_TYPE.canonicalName());
            DeclaredType declaredType = env.getTypeUtils().getDeclaredType(validatorElement, type.typeMirror);
            return new Type(declaredType.asElement(), declaredType);
        }
    }

    public record Field(Type type, String name, boolean isRecordOrInterface, boolean isNullable, boolean isNotNull,
                        boolean isJsonNullable, boolean isPrimitive, List<Constraint> constraint,
                        List<Validated> validates) {

        public String accessor() {
            return (isRecordOrInterface)
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

        /**
         * @param containers containers between the validated field and the constrained type argument, empty for a constraint of the field itself
         */
        public record Factory(Type type, Type validator, Map<String, Object> parameters, List<Container> containers) {

            public Factory(Type type, Type validator, Map<String, Object> parameters) {
                this(type, validator, parameters, List.of());
            }
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
