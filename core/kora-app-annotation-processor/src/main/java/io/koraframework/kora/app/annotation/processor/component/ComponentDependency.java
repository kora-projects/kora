package io.koraframework.kora.app.annotation.processor.component;

import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.WildcardTypeName;
import io.koraframework.annotation.processor.common.CommonClassNames;
import io.koraframework.kora.app.annotation.processor.ProcessingContext;
import io.koraframework.kora.app.annotation.processor.declaration.ComponentDeclaration;
import org.jspecify.annotations.Nullable;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public sealed interface ComponentDependency {

    DependencyClaim claim();

    default CodeBlock write(ProcessingContext ctx, ClassName graphTypeName, GraphHelperMethods helperMethods) {
        return switch (this) {
            case AllOfDependency allOf -> {
                var codeBlock = CodeBlock.builder();
                var method = switch (allOf.claim().claimType()) {
                    case ALL_OF_ONE -> "all";
                    case ALL_OF_VALUE -> "allValues";
                    case ALL_OF_PROMISE -> "allPromises";
                    default -> throw new IllegalStateException("Kora internal error: unsupported All<T> claim type for code generation: " + allOf.claim());
                };
                // javac inference time grows dramatically with the number of generic arguments when all of them are bound
                // to the same inferred type variable, so the type we already know is written explicitly
                var explicitType = explicitTypeArgument(allOf.claim().type(), graphTypeName);
                if (allOf.resolvedDependencies.size() >= GraphHelperMethods.WIDE_LIST_SIZE) {
                    var nodes = allOfNodesMethod(allOf.resolvedDependencies, explicitType, helperMethods);
                    yield explicitType == null
                        ? CodeBlock.of("$T.$L(g, $L())", CommonClassNames.all, method, nodes)
                        : CodeBlock.of("$T.<$T>$L(g, $L())", CommonClassNames.all, explicitType, method, nodes);
                }
                if (explicitType == null) {
                    codeBlock.add("$T.$L(g", CommonClassNames.all, method);
                } else {
                    codeBlock.add("$T.<$T>$L(g", CommonClassNames.all, explicitType, method);
                }
                for (var dependency : allOf.resolvedDependencies) {
                    if (explicitType == null) {
                        // type can't be written in the graph class, e.g. it is package-private in another package:
                        // cast makes every argument a standalone expression that is typed on its own, so there is still nothing to infer jointly
                        codeBlock.add(", ($T) $L", CommonClassNames.nodeWithMapper, nodeWithMapper(dependency, null));
                    } else {
                        codeBlock.add(", $L", nodeWithMapper(dependency, explicitType));
                    }
                }
                yield codeBlock.add(")").build();
            }
            case NullDependency(var claim) -> switch (claim.claimType()) {
                case ONE_NULLABLE -> CodeBlock.of("($T) null", claim.type());
                case NULLABLE_VALUE_OF -> CodeBlock.of("($T<$T>) null", CommonClassNames.valueOf, claim.type());
                case NULLABLE_PROMISE_OF -> CodeBlock.of("($T<$T>) null", CommonClassNames.promiseOf, claim.type());
                default -> throw new IllegalStateException("Kora internal error: unsupported nullable dependency claim type for code generation: " + claim);
            };
            case PromisedProxyParameterDependency promised -> {
                var dependency = Objects.requireNonNull(promised.realDependency);
                yield CodeBlock.of("g.promiseOf($T.$N.$N)", graphTypeName, dependency.holderName(), dependency.fieldName());
            }
            case PromiseOfDependency(_, var delegate) when delegate instanceof WrappedTargetDependency ->
                CodeBlock.of("g.promiseOf($T.$N.$N).map($T::value)", graphTypeName, delegate.component().holderName(), delegate.component().fieldName(), CommonClassNames.wrapped);
            case PromiseOfDependency(_, var delegate) -> CodeBlock.of("g.promiseOf($T.$N.$N)", graphTypeName, delegate.component().holderName(), delegate.component().fieldName());
            case TargetDependency(var claim, var component) -> switch (claim.claimType()) {
                case ONE_REQUIRED -> CodeBlock.of("g.get($T.$N.$N)", graphTypeName, component.holderName(), component.fieldName());
                case ONE_NULLABLE -> CodeBlock.of("g.getNullable($T.$N.$N)", graphTypeName, component.holderName(), component.fieldName());
                case NODE_OF -> CodeBlock.of("$T.$N.$N", graphTypeName, component.holderName(), component.fieldName());
                default -> throw new IllegalStateException("Kora internal error: unsupported target dependency claim type for code generation: " + claim);
            };
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
                    default -> throw new IllegalStateException("Kora internal error: unsupported one-of dependency claim type for code generation: " + oneOfDependency.claim());
                }
                var explicitType = explicitTypeArgument(oneOfDependency.claim().type(), graphTypeName);
                for (int i = 0; i < oneOfDependency.dependencies().size(); i++) {
                    if (i > 0) b.add(", ");
                    b.add(nodeWithMapper(oneOfDependency.dependencies().get(i), explicitType));
                }
                yield b.add(")").build();
            }
            case GraphDependency _ -> CodeBlock.of("g");
        };
    }

    /**
     * Factory with thousands of All&lt;T&gt; items does not fit into the method size limit, so array of items is built by a few methods
     *
     * @return name of the method that returns array of All&lt;T&gt; items
     */
    private static String allOfNodesMethod(List<SingleDependency> dependencies, @Nullable TypeMirror explicitType, GraphHelperMethods helperMethods) {
        var name = helperMethods.nextName("all");
        // type that can't be written in the graph class is left raw, items are still typed on their own
        var arrayType = explicitType == null
            ? ArrayTypeName.of(CommonClassNames.nodeWithMapper)
            : ArrayTypeName.of(ParameterizedTypeName.get(CommonClassNames.nodeWithMapper, WildcardTypeName.subtypeOf(Object.class), TypeName.get(explicitType)));
        var method = MethodSpec.methodBuilder(name)
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .returns(arrayType)
            .addStatement("$T nodes = new $T[$L]", arrayType, CommonClassNames.nodeWithMapper, dependencies.size());
        for (int from = 0, chunk = 0; from < dependencies.size(); from += GraphHelperMethods.WIDE_LIST_SIZE, chunk++) {
            var chunkName = name + "_" + chunk;
            var chunkMethod = MethodSpec.methodBuilder(chunkName)
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .addParameter(arrayType, "nodes");
            for (int i = from; i < Math.min(from + GraphHelperMethods.WIDE_LIST_SIZE, dependencies.size()); i++) {
                chunkMethod.addStatement("nodes[$L] = $L", i, nodeWithMapper(dependencies.get(i), explicitType));
            }
            helperMethods.add(chunkMethod.build());
            method.addStatement("$L(nodes)", chunkName);
        }
        helperMethods.add(method.addStatement("return nodes").build());
        return name;
    }

    private static CodeBlock nodeWithMapper(SingleDependency dependency, @Nullable TypeMirror explicitType) {
        var dependencyNode = dependency.component().nodeRef("some_fake_holder_idc");
        var isWrapped = switch (dependency) {
            case WrappedTargetDependency _ -> true;
            case ValueOfDependency valueOf -> valueOf.delegate instanceof WrappedTargetDependency;
            case PromiseOfDependency promiseOf -> promiseOf.delegate instanceof WrappedTargetDependency;
            case TargetDependency _ -> false;
        };
        if (isWrapped) {
            // wrapper type is inferred from the node itself, value type is inferred from the explicitly typed enclosing call
            return CodeBlock.of("$T.unwrap($L)", CommonClassNames.nodeWithMapper, dependencyNode);
        }
        if (explicitType == null) {
            return CodeBlock.of("$T.node($L)", CommonClassNames.nodeWithMapper, dependencyNode);
        }
        return CodeBlock.of("$T.<$T>node($L)", CommonClassNames.nodeWithMapper, explicitType, dependencyNode);
    }

    /**
     * @return type if it can be written as an explicit type argument in the generated graph class or null if it should be left to inference
     */
    @Nullable
    private static TypeMirror explicitTypeArgument(TypeMirror type, ClassName graphTypeName) {
        if (type.getKind() != TypeKind.DECLARED && type.getKind() != TypeKind.ARRAY) {
            return null;
        }
        return isDenotable(type, graphTypeName.packageName())
            ? type
            : null;
    }

    private static boolean isDenotable(TypeMirror type, String fromPackage) {
        if (type.getKind().isPrimitive()) {
            return true;
        }
        return switch (type.getKind()) {
            case ARRAY -> isDenotable(((ArrayType) type).getComponentType(), fromPackage);
            case WILDCARD -> {
                var wildcard = (WildcardType) type;
                yield (wildcard.getExtendsBound() == null || isDenotable(wildcard.getExtendsBound(), fromPackage))
                    && (wildcard.getSuperBound() == null || isDenotable(wildcard.getSuperBound(), fromPackage));
            }
            case DECLARED -> {
                var declaredType = (DeclaredType) type;
                Element element = declaredType.asElement();
                var isPublic = true;
                while (element instanceof TypeElement) {
                    if (element.getModifiers().contains(Modifier.PRIVATE)) {
                        yield false;
                    }
                    isPublic &= element.getModifiers().contains(Modifier.PUBLIC);
                    element = element.getEnclosingElement();
                }
                if (!(element instanceof PackageElement packageElement)) {
                    // local class
                    yield false;
                }
                if (!isPublic && !packageElement.getQualifiedName().contentEquals(fromPackage)) {
                    yield false;
                }
                for (var typeArgument : declaredType.getTypeArguments()) {
                    if (!isDenotable(typeArgument, fromPackage)) {
                        yield false;
                    }
                }
                yield true;
            }
            default -> false;
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
