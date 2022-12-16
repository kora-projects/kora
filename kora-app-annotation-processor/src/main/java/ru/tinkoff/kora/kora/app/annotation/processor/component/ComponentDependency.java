package ru.tinkoff.kora.kora.app.annotation.processor.component;

import com.squareup.javapoet.CodeBlock;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.kora.app.annotation.processor.GraphResolutionHelper;
import ru.tinkoff.kora.kora.app.annotation.processor.ProcessingContext;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.List;
import java.util.Set;

public sealed interface ComponentDependency {
    DependencyClaim claim();

    CodeBlock write(ProcessingContext ctx, List<ResolvedComponent> resolvedComponents, Set<Integer> promisedComponents);

    sealed interface SingleDependency extends ComponentDependency {
        ResolvedComponent component();
    }

    record TargetDependency(DependencyClaim claim, ResolvedComponent component) implements SingleDependency {

        @Override
        public CodeBlock write(ProcessingContext ctx, List<ResolvedComponent> resolvedComponents, Set<Integer> promisedComponents) {
            var node = promisedComponents.contains(component.index()) ? CodeBlock.of("$L.get()", this.component.name()) : CodeBlock.of("$L", this.component.name());
            return CodeBlock.of("g.get($L)", node);
        }
    }

    record WrappedTargetDependency(DependencyClaim claim, ResolvedComponent component) implements SingleDependency {

        @Override
        public CodeBlock write(ProcessingContext ctx, List<ResolvedComponent> resolvedComponents, Set<Integer> promisedComponents) {
            var node = promisedComponents.contains(component.index()) ? CodeBlock.of("$L.get()", this.component.name()) : CodeBlock.of("$L", this.component.name());
            return CodeBlock.of("g.get($L).value()", node);
        }
    }

    record NullDependency(DependencyClaim claim) implements ComponentDependency {
        @Override
        public CodeBlock write(ProcessingContext ctx, List<ResolvedComponent> resolvedComponents, Set<Integer> promisedComponents) {
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
        public CodeBlock write(ProcessingContext ctx, List<ResolvedComponent> resolvedComponents, Set<Integer> promisedComponents) {
            var node = promisedComponents.contains(delegate.component().index()) ? CodeBlock.of("$L.get()", delegate.component().name()) : CodeBlock.of("$L", delegate.component().name());
            if (this.delegate instanceof WrappedTargetDependency) {
                return CodeBlock.of("g.valueOf($L).map($T::value).map(v -> ($T) v)", node, CommonClassNames.wrapped, claim.type());
            }
            return CodeBlock.of("g.valueOf($L).map(v -> ($T) v)", node, claim.type());
        }

        @Override
        public ResolvedComponent component() {
            return delegate.component();
        }
    }

    record PromiseOfDependency(DependencyClaim claim, SingleDependency delegate) implements SingleDependency {
        @Override
        public CodeBlock write(ProcessingContext ctx, List<ResolvedComponent> resolvedComponents, Set<Integer> promisedComponents) {
            if (this.delegate instanceof WrappedTargetDependency) {
                return CodeBlock.of("g.promiseOf($L.get()).map($T::value).map(v -> ($T) v)", this.delegate.component().name(), CommonClassNames.wrapped, this.claim.type());
            }
            return CodeBlock.of("g.promiseOf($L.get()).map(v -> ($T) v)", delegate.component().name(), this.claim.type());
        }

        @Override
        public ResolvedComponent component() {
            return delegate.component();
        }
    }

    record TypeOfDependency(DependencyClaim claim) implements SingleDependency {
        @Override
        public CodeBlock write(ProcessingContext ctx, List<ResolvedComponent> resolvedComponents, Set<Integer> promisedComponents) {
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
        public CodeBlock write(ProcessingContext ctx, List<ResolvedComponent> resolvedComponents, Set<Integer> promisedComponents) {
            var codeBlock = CodeBlock.builder().add("$T.of(", CommonClassNames.all);
            var dependencies = GraphResolutionHelper.findDependenciesForAllOf(ctx, this.claim, resolvedComponents);
            for (int i = 0; i < dependencies.size(); i++) {
                var dependency = dependencies.get(i);
                if (i == 0) {
                    codeBlock.indent().add("\n");
                }
                codeBlock.add(dependency.write(ctx, resolvedComponents, promisedComponents));
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
        public CodeBlock write(ProcessingContext ctx, List<ResolvedComponent> resolvedComponents, Set<Integer> promisedComponents) {
            var dependencies = GraphResolutionHelper.findDependency(ctx, declaration, resolvedComponents, this.claim);
            return CodeBlock.of("g.promiseOf($L.get())", dependencies.component().name());
        }
    }
}
