package ru.tinkoff.kora.kora.app.annotation.processor.component;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.kora.app.annotation.processor.GraphResolutionHelper;
import ru.tinkoff.kora.kora.app.annotation.processor.ProcessingContext;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.List;

public sealed interface ComponentDependency {

    DependencyClaim claim();

    CodeBlock write(ProcessingContext ctx, ClassName graphTypeName, List<ResolvedComponent> resolvedComponents);

    sealed interface SingleDependency extends ComponentDependency {
        ResolvedComponent component();
    }

    record TargetDependency(DependencyClaim claim, ResolvedComponent component) implements SingleDependency {

        @Override
        public CodeBlock write(ProcessingContext ctx, ClassName graphTypeName, List<ResolvedComponent> resolvedComponents) {
            return CodeBlock.of("g.get($T.$N.$N)", graphTypeName, this.component.holderName(), this.component.fieldName());
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("TargetDependency[");
            sb.append("claim=").append(claim);
            sb.append(", index=").append(component.index());
            sb.append(", fieldName=").append(component.fieldName());
            sb.append(", holder=").append(component.holderName());
            sb.append(", component=").append(component.declaration());
            if (component.templateParams() != null && !component.templateParams().isEmpty()) {
                sb.append(", templateParams=").append(component.templateParams());
            }
            sb.append(']');
            return sb.toString();
        }
    }

    record WrappedTargetDependency(DependencyClaim claim, ResolvedComponent component) implements SingleDependency {

        @Override
        public CodeBlock write(ProcessingContext ctx, ClassName graphTypeName, List<ResolvedComponent> resolvedComponents) {
            return CodeBlock.of("g.get($T.$N.$N).value()", graphTypeName, this.component.holderName(), this.component.fieldName());
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("WrappedTargetDependency[");
            sb.append("claim=").append(claim);
            sb.append(", index=").append(component.index());
            sb.append(", fieldName=").append(component.fieldName());
            sb.append(", holder=").append(component.holderName());
            sb.append(", component=").append(component.declaration());
            if (component.templateParams() != null && !component.templateParams().isEmpty()) {
                sb.append(", templateParams=").append(component.templateParams());
            }
            sb.append(']');
            return sb.toString();
        }
    }

    record NullDependency(DependencyClaim claim) implements ComponentDependency {
        @Override
        public CodeBlock write(ProcessingContext ctx, ClassName graphTypeName, List<ResolvedComponent> resolvedComponents) {
            return switch (this.claim.claimType()) {
                case ONE_NULLABLE -> CodeBlock.of("($T) null", this.claim.type());
                case NULLABLE_VALUE_OF -> CodeBlock.of("($T<$T>) null", CommonClassNames.valueOf, this.claim.type());
                case NULLABLE_PROMISE_OF -> CodeBlock.of("($T<$T>) null", CommonClassNames.promiseOf, this.claim.type());
                default -> throw new IllegalArgumentException(this.claim.claimType().toString());
            };
        }
    }

    record ValueOfDependency(DependencyClaim claim, SingleDependency delegate) implements SingleDependency {
        @Override
        public CodeBlock write(ProcessingContext ctx, ClassName graphTypeName, List<ResolvedComponent> resolvedComponents) {
            if (this.delegate instanceof WrappedTargetDependency) {
                return CodeBlock.of("g.valueOf($T.$N.$N).map($T::value).map(v -> ($T) v)", graphTypeName, delegate.component().holderName(), delegate.component().fieldName(), CommonClassNames.wrapped, claim.type());
            }
            return CodeBlock.of("g.valueOf($T.$N.$N).map(v -> ($T) v)", graphTypeName, delegate.component().holderName(), delegate.component().fieldName(), claim.type());
        }

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
            sb.append(", component=").append(resolvedComponent.declaration());
            if (resolvedComponent.templateParams() != null && !resolvedComponent.templateParams().isEmpty()) {
                sb.append(", templateParams=").append(resolvedComponent.templateParams());
            }
            sb.append(']');
            return sb.toString();
        }
    }

    record PromiseOfDependency(DependencyClaim claim, SingleDependency delegate) implements SingleDependency {
        @Override
        public CodeBlock write(ProcessingContext ctx, ClassName graphTypeName, List<ResolvedComponent> resolvedComponents) {
            if (this.delegate instanceof WrappedTargetDependency) {
                return CodeBlock.of("g.promiseOf($T.$N.$N).map($T::value).map(v -> ($T) v)", graphTypeName, delegate.component().holderName(), this.delegate.component().fieldName(), CommonClassNames.wrapped, this.claim.type());
            }
            return CodeBlock.of("g.promiseOf($T.$N.$N).map(v -> ($T) v)", graphTypeName, delegate.component().holderName(), delegate.component().fieldName(), this.claim.type());
        }

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
            sb.append(", component=").append(resolvedComponent.declaration());
            if (resolvedComponent.templateParams() != null && !resolvedComponent.templateParams().isEmpty()) {
                sb.append(", templateParams=").append(resolvedComponent.templateParams());
            }
            sb.append(']');
            return sb.toString();
        }
    }

    record TypeOfDependency(DependencyClaim claim) implements SingleDependency {
        @Override
        public CodeBlock write(ProcessingContext ctx, ClassName graphTypeName, List<ResolvedComponent> resolvedComponents) {
            return this.buildTypeRef(ctx.types, this.claim.type());
        }

        private CodeBlock buildTypeRef(Types types, TypeMirror typeRef) {
            if (typeRef instanceof DeclaredType) {
                var b = CodeBlock.builder();
                var typeArguments = ((DeclaredType) typeRef).getTypeArguments();

                if (typeArguments.isEmpty()) {
                    b.add("$T.of($T.class)", TypeRef.class, types.erasure(typeRef));
                } else {
                    b.add("$T.<$T>of($T.class", TypeRef.class, typeRef, types.erasure(typeRef));
                    for (var typeArgument : typeArguments) {
                        b.add("$>,\n$L$<", buildTypeRef(types, typeArgument));
                    }
                    b.add("\n)");
                }
                return b.build();
            } else {
                return CodeBlock.of("$T.of($T.class)", TypeRef.class, typeRef);
            }
        }

        @Override
        public ResolvedComponent component() {
            return null;
        }
    }


    // AllOf dependencies has no resolved component: we will resolve them after graph building

    record AllOfDependency(DependencyClaim claim) implements ComponentDependency {
        @Override
        public CodeBlock write(ProcessingContext ctx, ClassName graphTypeName, List<ResolvedComponent> resolvedComponents) {
            var codeBlock = CodeBlock.builder().add("$T.of(", CommonClassNames.all);
            var dependencies = GraphResolutionHelper.findDependenciesForAllOf(ctx, this.claim, resolvedComponents);
            for (int i = 0; i < dependencies.size(); i++) {
                var dependency = dependencies.get(i);
                if (i == 0) {
                    codeBlock.indent().add("\n");
                }
                codeBlock.add(dependency.write(ctx, graphTypeName, resolvedComponents));
                if (i == dependencies.size() - 1) {
                    codeBlock.unindent();
                } else {
                    codeBlock.add(",");
                }
                codeBlock.add("\n");
            }

            return codeBlock.add("  )").build();
        }
    }

    record PromisedProxyParameterDependency(ru.tinkoff.kora.kora.app.annotation.processor.declaration.ComponentDeclaration declaration, DependencyClaim claim) implements ComponentDependency {

        @Override
        public CodeBlock write(ProcessingContext ctx, ClassName graphTypeName, List<ResolvedComponent> resolvedComponents) {
            var dependencies = GraphResolutionHelper.findDependency(ctx, declaration, resolvedComponents, this.claim);
            return CodeBlock.of("g.promiseOf($T.$N.$N)", graphTypeName, dependencies.component().holderName(), dependencies.component().fieldName());
        }

        @Override
        public String toString() {
            return "PromisedProxyParameterDependency[claim=" + claim + ", component=" + declaration + ']';
        }
    }
}
