package ru.tinkoff.kora.kora.app.annotation.processor.component;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.kora.app.annotation.processor.GraphResolutionHelper;
import ru.tinkoff.kora.kora.app.annotation.processor.ProcessingContext;
import ru.tinkoff.kora.kora.app.annotation.processor.declaration.ComponentDeclaration;
import ru.tinkoff.kora.kora.app.annotation.processor.declaration.ComponentDeclarations;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.Objects;

public sealed interface ComponentDependency {

    DependencyClaim claim();

    default CodeBlock write(ProcessingContext ctx, ClassName graphTypeName, ComponentDeclarations componentDeclarations, ResolvedComponents resolvedComponents) {
        return switch (this) {
            case AllOfDependency(var claim) -> {
                var dependencyDeclarations = GraphResolutionHelper.findDependencyDeclarations(ctx, componentDeclarations, claim);
                var codeBlock = CodeBlock.builder().add("$T.of(", CommonClassNames.all);
                var dependencies = GraphResolutionHelper.findDependenciesForAllOf(ctx, claim, dependencyDeclarations, resolvedComponents);
                for (int i = 0; i < dependencies.size(); i++) {
                    var dependency = dependencies.get(i);
                    if (i == 0) {
                        codeBlock.indent().add("\n");
                    }
                    codeBlock.add(dependency.write(ctx, graphTypeName, componentDeclarations, resolvedComponents));
                    if (i == dependencies.size() - 1) {
                        codeBlock.unindent();
                    } else {
                        codeBlock.add(",");
                    }
                    codeBlock.add("\n");
                }

                yield codeBlock.add("  )").build();
            }
            case NullDependency(var claim) -> switch (claim.claimType()) {
                case ONE_NULLABLE -> CodeBlock.of("($T) null", claim.type());
                case NULLABLE_VALUE_OF -> CodeBlock.of("($T<$T>) null", CommonClassNames.valueOf, claim.type());
                case NULLABLE_PROMISE_OF -> CodeBlock.of("($T<$T>) null", CommonClassNames.promiseOf, claim.type());
                default -> throw new IllegalArgumentException(claim.claimType().toString());
            };
            case PromisedProxyParameterDependency(_, var claim) -> {
                var declarations = GraphResolutionHelper.findDependencyDeclarations(ctx, componentDeclarations, claim);
                if (declarations.size() != 1) {
                    throw new IllegalStateException();
                }
                var dependency = Objects.requireNonNull(resolvedComponents.getByDeclaration(declarations.getFirst()));
                yield CodeBlock.of("g.promiseOf($T.$N.$N)", graphTypeName, dependency.holderName(), dependency.fieldName());
            }
            case PromiseOfDependency(var claim, var delegate) when delegate instanceof WrappedTargetDependency ->
                CodeBlock.of("g.promiseOf($T.$N.$N).map($T::value).map(v -> ($T) v)", graphTypeName, delegate.component().holderName(), delegate.component().fieldName(), CommonClassNames.wrapped, claim.type());
            case PromiseOfDependency(var claim, var delegate) ->
                CodeBlock.of("g.promiseOf($T.$N.$N).map(v -> ($T) v)", graphTypeName, delegate.component().holderName(), delegate.component().fieldName(), claim.type());
            case TargetDependency(var _, var component) -> CodeBlock.of("g.get($T.$N.$N)", graphTypeName, component.holderName(), component.fieldName());
            case TypeOfDependency(var claim) -> TypeOfDependency.buildTypeRef(ctx.types, claim.type());
            case ValueOfDependency(var claim, var delegate) when delegate instanceof WrappedTargetDependency ->
                CodeBlock.of("g.valueOf($T.$N.$N).map($T::value).map(v -> ($T) v)", graphTypeName, delegate.component().holderName(), delegate.component().fieldName(), CommonClassNames.wrapped, claim.type());
            case ValueOfDependency(var claim, var delegate) ->
                CodeBlock.of("g.valueOf($T.$N.$N).map(v -> ($T) v)", graphTypeName, delegate.component().holderName(), delegate.component().fieldName(), claim.type());
            case WrappedTargetDependency(var _, var component) -> CodeBlock.of("g.get($T.$N.$N).value()", graphTypeName, component.holderName(), component.fieldName());
        };
    }

    sealed interface SingleDependency extends ComponentDependency {
        ResolvedComponent component();
    }

    record TargetDependency(DependencyClaim claim, ResolvedComponent component) implements SingleDependency {

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("TargetDependency[");
            sb.append("claim=").append(claim);
            sb.append(", index=").append(component.index());
            sb.append(", fieldName=").append(component.fieldName());
            sb.append(", holder=").append(component.holderName());
            sb.append(", declaration=").append(component.declaration());
            if (component.templateParams() != null && !component.templateParams().isEmpty()) {
                sb.append(", templateParams=").append(component.templateParams());
            }
            sb.append(']');
            return sb.toString();
        }
    }

    record WrappedTargetDependency(DependencyClaim claim, ResolvedComponent component) implements SingleDependency {

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("WrappedTargetDependency[");
            sb.append("claim=").append(claim);
            sb.append(", index=").append(component.index());
            sb.append(", fieldName=").append(component.fieldName());
            sb.append(", holder=").append(component.holderName());
            sb.append(", declaration=").append(component.declaration());
            if (component.templateParams() != null && !component.templateParams().isEmpty()) {
                sb.append(", templateParams=").append(component.templateParams());
            }
            sb.append(']');
            return sb.toString();
        }
    }

    record NullDependency(DependencyClaim claim) implements ComponentDependency {
    }

    record ValueOfDependency(DependencyClaim claim, SingleDependency delegate) implements SingleDependency {
        @Override
        public ResolvedComponent component() {
            return delegate.component();
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("ValueOfDependency[");
            sb.append("claim=").append(claim);

            ResolvedComponent resolvedComponent = component();
            sb.append(", index=").append(resolvedComponent.index());
            sb.append(", fieldName=").append(resolvedComponent.fieldName());
            sb.append(", holder=").append(resolvedComponent.holderName());
            sb.append(", declaration=").append(resolvedComponent.declaration());
            if (resolvedComponent.templateParams() != null && !resolvedComponent.templateParams().isEmpty()) {
                sb.append(", templateParams=").append(resolvedComponent.templateParams());
            }
            sb.append(']');
            return sb.toString();
        }
    }

    record PromiseOfDependency(DependencyClaim claim, SingleDependency delegate) implements SingleDependency {
        @Override
        public ResolvedComponent component() {
            return delegate.component();
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("PromiseOfDependency[");
            sb.append("claim=").append(claim);

            ResolvedComponent resolvedComponent = component();
            sb.append(", index=").append(resolvedComponent.index());
            sb.append(", fieldName=").append(resolvedComponent.fieldName());
            sb.append(", holder=").append(resolvedComponent.holderName());
            sb.append(", declaration=").append(resolvedComponent.declaration());
            if (resolvedComponent.templateParams() != null && !resolvedComponent.templateParams().isEmpty()) {
                sb.append(", templateParams=").append(resolvedComponent.templateParams());
            }
            sb.append(']');
            return sb.toString();
        }
    }

    record TypeOfDependency(DependencyClaim claim) implements SingleDependency {
        private static CodeBlock buildTypeRef(Types types, TypeMirror typeRef) {
            if (typeRef instanceof DeclaredType) {
                var b = CodeBlock.builder();
                var typeArguments = ((DeclaredType) typeRef).getTypeArguments();

                if (typeArguments.isEmpty()) {
                    b.add("$T.of($T.class)", CommonClassNames.typeRef, types.erasure(typeRef));
                } else {
                    b.add("$T.<$T>of($T.class", CommonClassNames.typeRef, typeRef, types.erasure(typeRef));
                    for (var typeArgument : typeArguments) {
                        b.add("$>,\n$L$<", buildTypeRef(types, typeArgument));
                    }
                    b.add("\n)");
                }
                return b.build();
            } else {
                return CodeBlock.of("$T.of($T.class)", CommonClassNames.typeRef, typeRef);
            }
        }

        @Override
        public ResolvedComponent component() {
            return null;
        }
    }


    // AllOf dependencies has no resolved declaration: we will resolve them after graph building

    record AllOfDependency(DependencyClaim claim) implements ComponentDependency {
    }

    record PromisedProxyParameterDependency(ComponentDeclaration declaration, DependencyClaim claim) implements ComponentDependency {
        @Override
        public String toString() {
            return "PromisedProxyParameterDependency[claim=" + claim + ", declaration=" + declaration + ']';
        }
    }
}
