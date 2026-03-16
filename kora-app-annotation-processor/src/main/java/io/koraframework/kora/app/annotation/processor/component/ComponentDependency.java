package io.koraframework.kora.app.annotation.processor.component;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import io.koraframework.annotation.processor.common.CommonClassNames;
import io.koraframework.kora.app.annotation.processor.ProcessingContext;
import io.koraframework.kora.app.annotation.processor.declaration.ComponentDeclaration;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public sealed interface ComponentDependency {

    DependencyClaim claim();

    default CodeBlock write(ProcessingContext ctx, ClassName graphTypeName) {
        return switch (this) {
            case AllOfDependency allOf -> {
                var codeBlock = CodeBlock.builder();
                switch (allOf.claim().claimType()) {
                    case ALL_OF_ONE -> codeBlock.add("$T.all(g", CommonClassNames.all);
                    case ALL_OF_VALUE -> codeBlock.add("$T.allValues(g", CommonClassNames.all);
                    case ALL_OF_PROMISE -> codeBlock.add("$T.allPromises(g", CommonClassNames.all);
                    default -> throw new IllegalStateException("Unknown claim type: " + allOf.claim().claimType());
                }
                for (var dependency : allOf.resolvedDependencies) {
                    var dependencyNode = dependency.component().nodeRef("some_fake_holder_idc");
                    switch (dependency) {
                        case WrappedTargetDependency _ -> codeBlock.add(", $T.unwrap($L)", CommonClassNames.nodeWithMapper, dependencyNode);
                        case ValueOfDependency valueOf when valueOf.delegate instanceof WrappedTargetDependency -> codeBlock.add(", $T.unwrap($L)", CommonClassNames.nodeWithMapper, dependencyNode);
                        case PromiseOfDependency promiseOf when promiseOf.delegate instanceof WrappedTargetDependency ->
                            codeBlock.add(", $T.unwrap($L)", CommonClassNames.nodeWithMapper, dependencyNode);
                        default -> codeBlock.add(", $T.node($L)", CommonClassNames.nodeWithMapper, dependencyNode);
                    }
                }
                yield codeBlock.add(")").build();
            }
            case NullDependency(var claim) -> switch (claim.claimType()) {
                case ONE_NULLABLE -> CodeBlock.of("($T) null", claim.type());
                case NULLABLE_VALUE_OF -> CodeBlock.of("($T<$T>) null", CommonClassNames.valueOf, claim.type());
                case NULLABLE_PROMISE_OF -> CodeBlock.of("($T<$T>) null", CommonClassNames.promiseOf, claim.type());
                default -> throw new IllegalArgumentException(claim.claimType().toString());
            };
            case PromisedProxyParameterDependency promised -> {
                var dependency = Objects.requireNonNull(promised.realDependency);
                yield CodeBlock.of("g.promiseOf($T.$N.$N)", graphTypeName, dependency.holderName(), dependency.fieldName());
            }
            case PromiseOfDependency(_, var delegate) when delegate instanceof WrappedTargetDependency ->
                CodeBlock.of("g.promiseOf($T.$N.$N).map($T::value)", graphTypeName, delegate.component().holderName(), delegate.component().fieldName(), CommonClassNames.wrapped);
            case PromiseOfDependency(_, var delegate) -> CodeBlock.of("g.promiseOf($T.$N.$N)", graphTypeName, delegate.component().holderName(), delegate.component().fieldName());
            case TargetDependency(var _, var component) -> CodeBlock.of("g.get($T.$N.$N)", graphTypeName, component.holderName(), component.fieldName());
            case TypeOfDependency(var claim) -> TypeOfDependency.buildTypeRef(ctx.types, claim.type());
            case ValueOfDependency(_, var delegate) when delegate instanceof WrappedTargetDependency ->
                CodeBlock.of("g.valueOf($T.$N.$N).map($T::value)", graphTypeName, delegate.component().holderName(), delegate.component().fieldName(), CommonClassNames.wrapped);
            case ValueOfDependency(_, var delegate) -> CodeBlock.of("g.valueOf($T.$N.$N)", graphTypeName, delegate.component().holderName(), delegate.component().fieldName());
            case WrappedTargetDependency(var _, var component) -> CodeBlock.of("g.get($T.$N.$N).value()", graphTypeName, component.holderName(), component.fieldName());
            case OneOfDependency oneOfDependency -> {
                var b = CodeBlock.builder();
                switch (oneOfDependency.claim().claimType()) {
                    case ONE_REQUIRED -> {
                        b.add("g.getOneOf(");
                    }
                    case VALUE_OF -> {
                        b.add("g.getOneValueOf(");
                    }
                    case PROMISE_OF -> {
                        b.add("g.getOnePromiseOf(");
                    }
                    default -> throw new IllegalStateException("Unknown claim type: " + oneOfDependency.claim().claimType());
                }
                for (int i = 0; i < oneOfDependency.dependencies().size(); i++) {
                    if (i > 0) b.add(", ");
                    var dependencies = oneOfDependency.dependencies().get(i);
                    var dependencyNode = dependencies.component().nodeRef("some_fake_holder_idc");
                    switch (dependencies) {
                        case WrappedTargetDependency _ -> b.add("$T.unwrap($L)", CommonClassNames.nodeWithMapper, dependencyNode);
                        case ValueOfDependency valueOf when valueOf.delegate instanceof WrappedTargetDependency -> b.add("$T.unwrap($L)", CommonClassNames.nodeWithMapper, dependencyNode);
                        case PromiseOfDependency promiseOf when promiseOf.delegate instanceof WrappedTargetDependency -> b.add("$T.unwrap($L)", CommonClassNames.nodeWithMapper, dependencyNode);
                        default -> b.add("$T.node($L)", CommonClassNames.nodeWithMapper, dependencyNode);
                    }

                }
                yield b.add(")").build();
            }
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

    record OneOfDependency(DependencyClaim claim, List<SingleDependency> dependencies) implements ComponentDependency {}

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


    final class AllOfDependency implements ComponentDependency {
        private final DependencyClaim claim;
        // AllOf dependencies has no resolved declaration: we will resolve them after graph building
        private final List<SingleDependency> resolvedDependencies = new ArrayList<>();

        public AllOfDependency(DependencyClaim claim) {this.claim = claim;}

        @Override
        public DependencyClaim claim() {return claim;}

        public void addResolved(List<SingleDependency> resolvedComponents) {
            this.resolvedDependencies.addAll(resolvedComponents);
        }

        public List<SingleDependency> getResolvedDependencies() {
            return Collections.unmodifiableList(resolvedDependencies);
        }

        @Override
        public String toString() {
            return "AllOfDependency[claim=" + claim + ']';
        }

    }

    final class PromisedProxyParameterDependency implements ComponentDependency {
        private final ComponentDeclaration declaration;
        private final DependencyClaim claim;
        ResolvedComponent realDependency;

        public PromisedProxyParameterDependency(ComponentDeclaration declaration, DependencyClaim claim) {
            this.declaration = declaration;
            this.claim = claim;
        }

        @Override
        public String toString() {
            return "PromisedProxyParameterDependency[claim=" + claim + ", declaration=" + declaration + ']';
        }

        public ComponentDeclaration declaration() {return declaration;}

        @Override
        public DependencyClaim claim() {return claim;}


        public void setPromised(ResolvedComponent realDependency) {
            this.realDependency = realDependency;
        }
    }
}
