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
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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

    public static Type validatorOf(ProcessingEnvironment env, Type type) {
        TypeElement validatorElement = env.getElementUtils().getTypeElement(VALIDATOR_TYPE.canonicalName());
        DeclaredType declaredType = env.getTypeUtils().getDeclaredType(validatorElement, type.typeMirror);
        return new Type(declaredType.asElement(), declaredType);
    }

    public enum Container {
        ITERABLE,
        MAP_KEYS,
        MAP_VALUES
    }

    /**
     * Validation put on a type and on its type arguments, like {@code List<@Valid Item>} or {@code Map<String, @Size(max = 5) String>}
     *
     * @param constraints constraints of the type itself
     * @param validated   the type itself when it is marked with {@code @Valid}
     * @param children    validation of the type arguments when the type is a collection or a map
     */
    public record TypeUse(List<Constraint> constraints, List<Type> validated, Map<Container, TypeUse> children) {

        private static final ClassName CONTAINER_VALIDATORS = ClassName.get("io.koraframework.validation.common.constraint", "ContainerValidators");

        public boolean isEmpty() {
            return constraints.isEmpty() && validated.isEmpty() && children.isEmpty();
        }

        /**
         * @param constraintValidator code that creates a validator from a constraint factory
         * @param validValidator      code that refers to a validator of the given type
         * @return validator of the container that checks all its elements in a single pass
         */
        public CodeBlock containerValidator(Function<Constraint.Factory, CodeBlock> constraintValidator, Function<Type, CodeBlock> validValidator) {
            if (children.containsKey(Container.ITERABLE)) {
                return CodeBlock.of("$T.iterable($L)", CONTAINER_VALIDATORS, children.get(Container.ITERABLE).validator(constraintValidator, validValidator));
            }
            var keys = children.get(Container.MAP_KEYS);
            var values = children.get(Container.MAP_VALUES);
            return CodeBlock.of("$T.map($L, $L)", CONTAINER_VALIDATORS,
                keys == null ? CodeBlock.of("null") : keys.validator(constraintValidator, validValidator),
                values == null ? CodeBlock.of("null") : values.validator(constraintValidator, validValidator));
        }

        private CodeBlock validator(Function<Constraint.Factory, CodeBlock> constraintValidator, Function<Type, CodeBlock> validValidator) {
            var validators = new ArrayList<CodeBlock>();
            for (var constraint : constraints) {
                validators.add(constraintValidator.apply(constraint.factory()));
            }
            for (var type : validated) {
                validators.add(validValidator.apply(type));
            }
            if (!children.isEmpty()) {
                validators.add(containerValidator(constraintValidator, validValidator));
            }
            return validators.size() == 1
                ? validators.get(0)
                : CodeBlock.of("$T.all($L)", CONTAINER_VALIDATORS, CodeBlock.join(validators, ", "));
        }
    }

    /**
     * @param target  validated type
     * @param typeUse validation put on type arguments of the target, {@code null} when the target itself is marked with {@code @Valid}
     */
    public record Validated(Type target, @Nullable TypeUse typeUse) {

        public Validated(Type target) {
            this(target, null);
        }

        public Type validator(ProcessingEnvironment env) {
            return validatorOf(env, target);
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
