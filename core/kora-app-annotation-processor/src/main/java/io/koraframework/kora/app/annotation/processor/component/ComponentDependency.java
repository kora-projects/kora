package io.koraframework.kora.app.annotation.processor.component;

import com.palantir.javapoet.*;
import io.koraframework.annotation.processor.common.*;
import io.koraframework.kora.app.annotation.processor.*;
import io.koraframework.kora.app.annotation.processor.declaration.*;

import javax.lang.model.type.*;
import javax.lang.model.util.*;
import java.util.*;

public sealed interface ComponentDependency {

    DependencyClaim claim();

    default CodeBlock write(ProcessingContext ctx) {
        return switch (this) {
            case AllOfDependency allOf -> {
                var codeBlock = CodeBlock.builder();
                switch (allOf.claim().claimType()) {
                    case ALL_OF_ONE -> codeBlock.add("all(g");
                    case ALL_OF_VALUE -> codeBlock.add("allValues(g");
                    case ALL_OF_PROMISE -> codeBlock.add("allPromises(g");
                    default -> throw new IllegalStateException("Kora internal error: unsupported All<T> claim type for code generation: " + allOf.claim());
                }
                for (var dependency : allOf.resolvedDependencies) {
                    var comp = dependency.component();
                    var dependencyNode = resolveNodePath(ctx, comp);
                    switch (dependency) {
                        case WrappedTargetDependency _ -> codeBlock.add(", unwrap($L)", dependencyNode);
                        case ValueOfDependency valueOf when valueOf.delegate instanceof WrappedTargetDependency -> codeBlock.add(", unwrap($L)", dependencyNode);
                        case PromiseOfDependency promiseOf when promiseOf.delegate instanceof WrappedTargetDependency -> codeBlock.add(", unwrap($L)", dependencyNode);
                        default -> codeBlock.add(", node($L)", dependencyNode);
                    }
                }
                yield codeBlock.add(")").build();
            }
            case NullDependency(var claim) -> switch (claim.claimType()) {
                case ONE_NULLABLE, NULLABLE_VALUE_OF, NULLABLE_PROMISE_OF -> CodeBlock.of("null");
                default -> throw new IllegalStateException("Kora internal error: unsupported nullable dependency claim type for code generation: " + claim);
            };
            case PromisedProxyParameterDependency promised -> {
                var dependency = Objects.requireNonNull(promised.realDependency);
                yield CodeBlock.of("g.promiseOf($L)", resolveNodePath(ctx, dependency));
            }
            case PromiseOfDependency(_, var delegate) when delegate instanceof WrappedTargetDependency -> {
                var comp = delegate.component();
                yield CodeBlock.of("g.promiseOf($L).map($T::value)", resolveNodePath(ctx, comp), CommonClassNames.wrapped);
            }
            case PromiseOfDependency(_, var delegate) -> {
                var comp = delegate.component();
                yield CodeBlock.of("g.promiseOf($L)", resolveNodePath(ctx, comp));
            }
            case TargetDependency(var claim, var component) -> {
                var nodePath = resolveNodePath(ctx, component);
                yield switch (claim.claimType()) {
                    case ONE_REQUIRED -> CodeBlock.of("g.get($L)", nodePath);
                    case ONE_NULLABLE -> CodeBlock.of("g.getNullable($L)", nodePath);
                    case NODE_OF -> nodePath;
                    default -> throw new IllegalStateException("Kora internal error: unsupported target dependency claim type for code generation: " + claim);
                };
            }
            case TypeOfDependency(var claim) -> TypeOfDependency.buildTypeRef(ctx.types, claim.type());
            case ValueOfDependency(_, var delegate) when delegate instanceof WrappedTargetDependency -> {
                var comp = delegate.component();
                yield CodeBlock.of("g.valueOf($L).map($T::value)", resolveNodePath(ctx, comp), CommonClassNames.wrapped);
            }
            case ValueOfDependency(_, var delegate) -> {
                var comp = delegate.component();
                yield CodeBlock.of("g.valueOf($L)", resolveNodePath(ctx, comp));
            }
            case WrappedTargetDependency(_, var component) -> {
                yield CodeBlock.of("g.get($L).value()", resolveNodePath(ctx, component));
            }
            case OneOfDependency oneOfDependency -> {
                var b = CodeBlock.builder();
                switch (oneOfDependency.claim().claimType()) {
                    case ONE_REQUIRED -> b.add("g.getOneOf(");
                    case VALUE_OF -> b.add("g.getOneValueOf(");
                    case PROMISE_OF -> b.add("g.getOnePromiseOf(");
                    default -> throw new IllegalStateException("Kora internal error: unsupported one-of dependency claim type for code generation: " + oneOfDependency.claim());
                }
                for (var i = 0; i < oneOfDependency.dependencies().size(); i++) {
                    if (i > 0) b.add(", ");
                    var dependencies = oneOfDependency.dependencies().get(i);
                    var comp = dependencies.component();
                    var dependencyNode = resolveNodePath(ctx, comp);

                    switch (dependencies) {
                        case WrappedTargetDependency _ -> b.add("$T.unwrap($L)", CommonClassNames.nodeWithMapper, dependencyNode);
                        case ValueOfDependency valueOf when valueOf.delegate instanceof WrappedTargetDependency -> b.add("$T.unwrap($L)", CommonClassNames.nodeWithMapper, dependencyNode);
                        case PromiseOfDependency promiseOf when promiseOf.delegate instanceof WrappedTargetDependency -> b.add("$T.unwrap($L)", CommonClassNames.nodeWithMapper, dependencyNode);
                        default -> b.add("$T.node($L)", CommonClassNames.nodeWithMapper, dependencyNode);
                    }
                }
                yield b.add(")").build();
            }
            case GraphDependency _ -> CodeBlock.of("g");
        };
    }

    default CodeBlock resolveNodePath(ProcessingContext ctx, ResolvedComponent comp) {
        int globalIdx = comp.index();
        int targetPartition = ctx.partitionOf(globalIdx);
        if (targetPartition != ctx.currentPartition) {
            return CodeBlock.of("Partition$L.$L", targetPartition, comp.fieldName());
        }
        return CodeBlock.of("$L", comp.fieldName());
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

    record TypeOfDependency(DependencyClaim claim) implements ComponentDependency {
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
    }

    record GraphDependency(DependencyClaim claim) implements ComponentDependency {}


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
        public ResolvedComponent realDependency;

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
