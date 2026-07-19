package io.koraframework.kora.app.annotation.processor.declaration;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.TypeName;
import io.koraframework.annotation.processor.common.*;
import io.koraframework.kora.app.annotation.processor.ProcessingContext;
import io.koraframework.kora.app.annotation.processor.extension.ExtensionResult;
import org.jspecify.annotations.Nullable;

import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public sealed interface ComponentDeclaration {
    TypeMirror type();

    Element source();

    @Nullable
    String tag();

    default boolean isTemplate() {
        return TypeParameterUtils.hasTypeParameter(this.type());
    }

    default boolean isDefault() {
        return false;
    }

    boolean isInterceptor();

    String declarationString();

    @Nullable
    ClassName condition();

    record FromModuleComponent(TypeMirror type, ModuleDeclaration module, @Nullable String tag, @Nullable ClassName condition, ExecutableElement method, List<TypeMirror> methodParameterTypes,
                               List<TypeMirror> typeVariables, boolean isInterceptor) implements ComponentDeclaration {
        @Override
        public Element source() {
            return this.method;
        }

        @Override
        public boolean isDefault() {
            return AnnotationUtils.findAnnotation(this.method, CommonClassNames.defaultComponent) != null;
        }

        @Override
        public String declarationString() {
            String args = method.getParameters().isEmpty()
                ? "()"
                : "(...)";
            return "factory  " + module.element().getQualifiedName() + "#" + method.getSimpleName() + args;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("FromModuleComponent[");
            sb.append("type=").append(type);
            sb.append(", module=").append(module);
            sb.append(", method=").append(method);
            if (tag != null) {
                sb.append(", tag=").append(tag);
            }
            if (methodParameterTypes != null && !methodParameterTypes.isEmpty()) {
                sb.append(", methodParameterTypes=").append(methodParameterTypes);
            }
            if (typeVariables != null && !typeVariables.isEmpty()) {
                sb.append(", typeVariables=").append(typeVariables);
            }
            if (isInterceptor) {
                sb.append(", isInterceptor=").append(isInterceptor);
            }
            sb.append(']');
            return sb.toString();
        }
    }

    record AnnotatedComponent(TypeMirror type, TypeElement typeElement, @Nullable String tag, @Nullable ClassName condition, ExecutableElement constructor, List<TypeMirror> methodParameterTypes,
                              List<TypeMirror> typeVariables, boolean isInterceptor) implements ComponentDeclaration {
        @Override
        public Element source() {
            return this.constructor;
        }

        @Override
        public boolean isDefault() {
            return AnnotationUtils.findAnnotation(this.typeElement, CommonClassNames.defaultComponent) != null;
        }

        @Override
        public String declarationString() {
            return "component  " + TypeName.get(type);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("AnnotatedComponent[");
            sb.append("type=").append(type);
            sb.append(", typeElement=").append(typeElement);
            sb.append(", constructor=").append(constructor);
            if (tag != null) {
                sb.append(", tag=").append(tag);
            }
            if (methodParameterTypes != null && !methodParameterTypes.isEmpty()) {
                sb.append(", methodParameterTypes=").append(methodParameterTypes);
            }
            if (typeVariables != null && !typeVariables.isEmpty()) {
                sb.append(", typeVariables=").append(typeVariables);
            }
            if (isInterceptor) {
                sb.append(", isInterceptor=").append(isInterceptor);
            }
            sb.append(']');
            return sb.toString();
        }
    }

    record FromExtensionComponent(
        TypeMirror type,
        Element source,
        List<TypeMirror> dependencyTypes,
        List<String> dependencyTags,
        @Nullable String tag,
        Function<CodeBlock, CodeBlock> generator
    ) implements ComponentDeclaration {
        @Override
        public boolean isInterceptor() {
            return false;
        }

        @Override
        public String declarationString() {
            return "extension  " + source.getEnclosingElement().toString() + "." + source.getSimpleName();
        }

        @Override
        public @Nullable ClassName condition() {
            return null;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("FromExtensionComponent[");
            sb.append("type=").append(type);
            sb.append(", source=").append(source);
            if (tag != null) {
                sb.append(", tag=").append(tag);
            }
            if (dependencyTypes != null && !dependencyTypes.isEmpty()) {
                sb.append(", dependencyTypes=").append(dependencyTypes);
            }
            if (dependencyTags != null && dependencyTags.stream().anyMatch(Objects::nonNull)) {
                sb.append(", dependencyTags=").append(dependencyTags);
            }
            sb.append(']');
            return sb.toString();
        }
    }

    record PromisedProxyComponent(TypeElement typeElement, TypeMirror type, com.palantir.javapoet.ClassName className) implements ComponentDeclaration {
        public PromisedProxyComponent(TypeElement typeElement, com.palantir.javapoet.ClassName className) {
            this(typeElement, typeElement.asType(), className);
        }

        public PromisedProxyComponent withType(TypeMirror type) {
            return new PromisedProxyComponent(typeElement, type, className);
        }


        @Override
        public Element source() {
            return this.typeElement;
        }

        @Override
        public String tag() {
            return CommonClassNames.promisedProxy.canonicalName();
        }

        @Override
        public boolean isInterceptor() {
            return false;
        }

        @Override
        public String declarationString() {
            return "<Proxy>";
        }

        @Override
        public @Nullable ClassName condition() {
            return null;
        }
    }

    record OptionalComponent(TypeMirror type, @Nullable String tag) implements ComponentDeclaration {
        @Override
        public Element source() {
            return null;
        }

        @Override
        public boolean isInterceptor() {
            return false;
        }

        @Override
        public String declarationString() {
            return "<EmptyOptional>";
        }

        @Override
        public @Nullable ClassName condition() {
            return null;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("OptionalComponent[");
            sb.append("type=").append(type);
            if (tag != null) {
                sb.append(", tag=").append(tag);
            }
            sb.append(']');
            return sb.toString();
        }
    }

    static ComponentDeclaration fromModule(ProcessingContext ctx, ModuleDeclaration module, ExecutableElement method) {
        var type = method.getReturnType();
        if (TypeParameterUtils.hasRawTypes(type)) {
            throw new ProcessingErrorException("""
                Component provider returns a raw type:
                  type: %s

                Raw component types are forbidden because they make dependency resolution ambiguous.

                Fix:
                  - Specify generic type arguments in the return type.
                  - Return a concrete parameterized type.
                """.formatted(type).stripTrailing(), method);
        }
        var tag = TagUtils.parseTagValue(method);
        if (CommonClassNames.tagFactory.canonicalName().equals(tag)) {
            if (module instanceof ModuleDeclaration.FactoryModule(var _, var moduleTag)) {
                tag = moduleTag;
            } else {
                throw new ProcessingErrorException("""
                    @Tag.Factory can only be used inside factory modules.

                    Fix:
                      - Move this provider to a factory module.
                      - Replace @Tag.Factory with an explicit @Tag(...) value.
                    """.stripTrailing(), method);
            }
        }
        var conditionalAnnotation = AnnotationUtils.findAnnotation(method, CommonClassNames.conditional);
        var condition = conditionalAnnotation != null
            ? (ClassName) TypeName.get(Objects.requireNonNull(AnnotationUtils.<TypeMirror>parseAnnotationValueWithoutDefault(conditionalAnnotation, "tag")))
            : null;
        var parameterTypes = method.getParameters().stream().map(VariableElement::asType).toList();
        var typeParameters = method.getTypeParameters().stream().map(TypeParameterElement::asType).toList();
        var isInterceptor = ctx.serviceTypeHelper.isInterceptor(type);
        return new FromModuleComponent(type, module, tag, condition, method, parameterTypes, typeParameters, isInterceptor);
    }

    static ComponentDeclaration fromAnnotated(ProcessingContext ctx, TypeElement typeElement) {
        var constructors = CommonUtils.findConstructors(typeElement, m -> m.contains(Modifier.PUBLIC));
        if (constructors.size() != 1) {
            throw new ProcessingErrorException("""
                @Component class must have exactly one public constructor.

                Fix:
                  - Keep one public constructor.
                  - Make extra constructors non-public.
                  - Move complex construction logic to a module method.
                """.stripTrailing(), typeElement);
        }
        var constructor = constructors.get(0);
        var type = typeElement.asType();
        if (TypeParameterUtils.hasRawTypes(type)) {
            ctx.messager.printMessage(Diagnostic.Kind.WARNING, "Components with raw types can break dependency resolution in unpredictable way", typeElement);
        }
        if (AnnotationUtils.isAnnotationPresent(typeElement, CommonClassNames.aopProxy)) {
            type = typeElement.getSuperclass();
        }
        var tags = TagUtils.parseTagValue(typeElement);
        var conditionalAnnotation = AnnotationUtils.findAnnotation(typeElement, CommonClassNames.conditional);
        var condition = conditionalAnnotation != null
            ? (ClassName) TypeName.get(Objects.requireNonNull(AnnotationUtils.<TypeMirror>parseAnnotationValueWithoutDefault(conditionalAnnotation, "tag")))
            : null;
        var parameterTypes = constructor.getParameters().stream().map(VariableElement::asType).toList();
        var typeParameters = typeElement.getTypeParameters().stream().map(TypeParameterElement::asType).toList();
        var isInterceptor = ctx.serviceTypeHelper.isInterceptor(type);
        return new AnnotatedComponent(type, typeElement, tags, condition, constructor, parameterTypes, typeParameters, isInterceptor);
    }

    static ComponentDeclaration fromExtension(ProcessingContext ctx, ExtensionResult.GeneratedResult generatedResult) {
        var sourceMethod = generatedResult.sourceElement();
        if (sourceMethod.getKind() == ElementKind.CONSTRUCTOR) {
            var parameterTypes = sourceMethod.getParameters().stream().map(VariableElement::asType).toList();
            var parameterTags = sourceMethod.getParameters().stream().map(TagUtils::parseTagValue).toList();
            var typeElement = (TypeElement) sourceMethod.getEnclosingElement();
            var tag = TagUtils.parseTagValue(sourceMethod);
            if (tag == null) {
                tag = TagUtils.parseTagValue(typeElement);
            }
            var type = typeElement.asType();
            if (TypeParameterUtils.hasRawTypes(type)) {
                throw new ProcessingErrorException("""
                    Extension component uses a raw type:
                      type: %s

                    Raw component types are forbidden because they make dependency resolution ambiguous.

                    Fix:
                      - Specify generic type arguments explicitly.
                      - Generate a concrete parameterized component type.
                    """.formatted(type).stripTrailing(), sourceMethod);
            }
            var className = ClassName.get(typeElement);

            return new FromExtensionComponent(type, sourceMethod, parameterTypes, parameterTags, tag, dependencies -> typeElement.getTypeParameters().isEmpty()
                ? CodeBlock.of("new $T($L)", className, dependencies)
                : CodeBlock.of("new $T<>($L)", className, dependencies));
        } else {
            var type = generatedResult.targetType().getReturnType();
            var parameterTypes = generatedResult.targetType().getParameterTypes();
            if (TypeParameterUtils.hasRawTypes(type)) {
                throw new ProcessingErrorException("""
                    Extension component provider returns a raw type:
                      type: %s

                    Raw component types are forbidden because they make dependency resolution ambiguous.

                    Fix:
                      - Specify generic type arguments explicitly.
                      - Generate a concrete parameterized return type.
                    """.formatted(type).stripTrailing(), sourceMethod);
            }
            var parameterTags = sourceMethod.getParameters().stream().map(TagUtils::parseTagValue).toList();
            var tag = TagUtils.parseTagValue(sourceMethod);
            var typeElement = (TypeElement) sourceMethod.getEnclosingElement();
            var className = ClassName.get(typeElement);
            return new FromExtensionComponent(type, sourceMethod, new ArrayList<>(parameterTypes), parameterTags, tag, dependencies -> CodeBlock.of("$T.$N($L)", className, sourceMethod.getSimpleName(), dependencies));
        }
    }

    static ComponentDeclaration fromExtension(ExtensionResult.CodeBlockResult generatedResult) {
        return new FromExtensionComponent(
            generatedResult.componentType(),
            generatedResult.source(),
            generatedResult.dependencyTypes(),
            generatedResult.dependencyTags(),
            generatedResult.componentTag(),
            generatedResult.codeBlock()
        );
    }
}
