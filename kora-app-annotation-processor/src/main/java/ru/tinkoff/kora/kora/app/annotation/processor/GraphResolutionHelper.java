package ru.tinkoff.kora.kora.app.annotation.processor;

import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.kora.app.annotation.processor.component.ComponentDependency;
import ru.tinkoff.kora.kora.app.annotation.processor.component.DependencyClaim;
import ru.tinkoff.kora.kora.app.annotation.processor.component.ResolvedComponent;
import ru.tinkoff.kora.kora.app.annotation.processor.component.ResolvedComponents;
import ru.tinkoff.kora.kora.app.annotation.processor.declaration.ComponentDeclaration;
import ru.tinkoff.kora.kora.app.annotation.processor.declaration.ComponentDeclarations;
import ru.tinkoff.kora.kora.app.annotation.processor.declaration.DeclarationWithIndex;

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import static ru.tinkoff.kora.kora.app.annotation.processor.component.DependencyClaim.DependencyClaimType.*;

public final class GraphResolutionHelper {

    private GraphResolutionHelper() {}

    public static List<DeclarationWithIndex> findDependencyDeclarations(ProcessingContext ctx, ComponentDeclarations declarationMap, DependencyClaim dependencyClaim) {
        var declarations = declarationMap.getByType(dependencyClaim.type());
        if (declarations == null) {
            return List.of();
        }
        var result = new ArrayList<DeclarationWithIndex>();
        for (var sourceDeclaration : declarations) {
            if (sourceDeclaration.declaration().isTemplate()) {
                continue;
            }
            if (!dependencyClaim.tagsMatches(sourceDeclaration.declaration().tag())) {
                continue;
            }
            if (ctx.types.isAssignable(sourceDeclaration.declaration().type(), dependencyClaim.type()) || ctx.serviceTypeHelper.isAssignableToUnwrapped(sourceDeclaration.declaration().type(), dependencyClaim.type())) {
                result.add(sourceDeclaration);
            }
        }
        return result;
    }

    public static ComponentDependency.SingleDependency toDependency(ProcessingContext ctx, ResolvedComponent resolvedComponent, DependencyClaim dependencyClaim) {
        var isDirectAssignable = ctx.types.isAssignable(resolvedComponent.type(), dependencyClaim.type());
        var isWrappedAssignable = ctx.serviceTypeHelper.isAssignableToUnwrapped(resolvedComponent.type(), dependencyClaim.type());
        if (!isDirectAssignable && !isWrappedAssignable) {
            throw new IllegalStateException();
        }

        var targetDependency = isWrappedAssignable
            ? new ComponentDependency.WrappedTargetDependency(dependencyClaim, resolvedComponent)
            : new ComponentDependency.TargetDependency(dependencyClaim, resolvedComponent);

        return switch (dependencyClaim.claimType()) {
            case ONE_REQUIRED, ONE_NULLABLE -> targetDependency;
            case PROMISE_OF, NULLABLE_PROMISE_OF -> new ComponentDependency.PromiseOfDependency(dependencyClaim, targetDependency);
            case VALUE_OF, NULLABLE_VALUE_OF -> new ComponentDependency.ValueOfDependency(dependencyClaim, targetDependency);
            case ALL_OF_ONE, ALL_OF_PROMISE, ALL_OF_VALUE, TYPE_REF -> throw new IllegalStateException();
        };
    }

    public static List<ComponentDependency.SingleDependency> findDependenciesForAllOf(ProcessingContext ctx, DependencyClaim dependencyClaim, List<DeclarationWithIndex> declarations, ResolvedComponents resolvedComponents) {
        var claimType = dependencyClaim.claimType();
        var result = new ArrayList<ComponentDependency.SingleDependency>();
        for (var declarationWithIndex : declarations) {
            var declaration = declarationWithIndex.declaration();
            if (!dependencyClaim.tagsMatches(declaration.tag())) {
                continue;
            }
            var component = Objects.requireNonNull(resolvedComponents.getByDeclaration(declarationWithIndex));
            if (ctx.types.isAssignable(declaration.type(), dependencyClaim.type())) {
                var targetDependency = new ComponentDependency.TargetDependency(dependencyClaim, component);
                var dependency = switch (claimType) {
                    case ALL_OF_ONE -> targetDependency;
                    case ALL_OF_PROMISE -> new ComponentDependency.PromiseOfDependency(dependencyClaim, targetDependency);
                    case ALL_OF_VALUE -> new ComponentDependency.ValueOfDependency(dependencyClaim, targetDependency);
                    case null, default -> throw new IllegalStateException("Unexpected value: " + dependencyClaim.claimType());
                };
                result.add(dependency);
            }
            if (ctx.serviceTypeHelper.isAssignableToUnwrapped(declaration.type(), dependencyClaim.type())) {
                var targetDependency = new ComponentDependency.WrappedTargetDependency(dependencyClaim, component);
                var dependency = switch (claimType) {
                    case ALL_OF_ONE -> targetDependency;
                    case ALL_OF_PROMISE -> new ComponentDependency.PromiseOfDependency(dependencyClaim, targetDependency);
                    case ALL_OF_VALUE -> new ComponentDependency.ValueOfDependency(dependencyClaim, targetDependency);
                    case null, default -> throw new IllegalStateException("Unexpected value: " + dependencyClaim.claimType());
                };
                result.add(dependency);
            }
        }
        return result;
    }

    public static List<ComponentDeclaration> findDependencyDeclarationsFromTemplate(ProcessingContext ctx, ComponentDeclaration forDeclaration, List<ComponentDeclaration> sourceDeclarations, DependencyClaim dependencyClaim) {
        if (dependencyClaim.type().getKind() == TypeKind.ERROR) {
            throw new ProcessingErrorException("Component error type dependency claim " + dependencyClaim.type(), forDeclaration.source());
        }

        var claimType = dependencyClaim.claimType();
        if (claimType == ALL_OF_ONE || claimType == ALL_OF_PROMISE || claimType == ALL_OF_VALUE) {
            throw new UnsupportedOperationException();
        }
        var types = ctx.types;
        var declarations = new ArrayList<ComponentDeclaration>();
        sources:
        for (var sourceDeclaration : sourceDeclarations) {
            if (!sourceDeclaration.isTemplate()) {
                continue;
            }
            if (!dependencyClaim.tagsMatches(sourceDeclaration.tag())) {
                continue sources;
            }
            var requiredDeclaredType = (DeclaredType) dependencyClaim.type();
            var declarationDeclaredType = (DeclaredType) sourceDeclaration.type();
            var match = ComponentTemplateHelper.match(ctx, declarationDeclaredType, requiredDeclaredType);
            if (match instanceof ComponentTemplateHelper.TemplateMatch.None) {
                continue sources;
            }
            if (!(match instanceof ComponentTemplateHelper.TemplateMatch.Some(var map))) {
                throw new IllegalStateException();
            }
            var realReturnType = ComponentTemplateHelper.replace(types, declarationDeclaredType, map);

            switch (sourceDeclaration) {
                case ComponentDeclaration.FromModuleComponent declaredComponent -> {
                    var realParams = new ArrayList<TypeMirror>(declaredComponent.methodParameterTypes().size());
                    for (var methodParameterType : declaredComponent.methodParameterTypes()) {
                        realParams.add(ComponentTemplateHelper.replace(types, methodParameterType, map));
                    }
                    var typeParameters = new ArrayList<TypeMirror>();
                    for (int i = 0; i < declaredComponent.method().getTypeParameters().size(); i++) {
                        typeParameters.add(ComponentTemplateHelper.replace(types, declaredComponent.method().getTypeParameters().get(i).asType(), map));
                    }
                    declarations.add(new ComponentDeclaration.FromModuleComponent(
                        realReturnType,
                        declaredComponent.module(),
                        declaredComponent.tag(),
                        declaredComponent.method(),
                        realParams,
                        typeParameters,
                        declaredComponent.isInterceptor()
                    ));
                }
                case ComponentDeclaration.AnnotatedComponent annotatedComponent -> {
                    var realParams = new ArrayList<TypeMirror>();
                    for (var methodParameterType : annotatedComponent.methodParameterTypes()) {
                        realParams.add(ComponentTemplateHelper.replace(types, methodParameterType, map));
                    }
                    var typeParameters = new ArrayList<TypeMirror>();
                    for (int i = 0; i < annotatedComponent.typeElement().getTypeParameters().size(); i++) {
                        typeParameters.add(ComponentTemplateHelper.replace(types, annotatedComponent.typeElement().getTypeParameters().get(i).asType(), map));
                    }
                    declarations.add(new ComponentDeclaration.AnnotatedComponent(
                        realReturnType,
                        annotatedComponent.typeElement(),
                        annotatedComponent.tag(),
                        annotatedComponent.constructor(),
                        realParams,
                        typeParameters,
                        annotatedComponent.isInterceptor()
                    ));
                }
                case ComponentDeclaration.FromExtensionComponent extensionComponent -> {
                    var realParams = new ArrayList<TypeMirror>();
                    // idk what's happening here, but somehow we've got some different identity tpe that can't be replaced
                    for (var tpe : collectTypeVariables(extensionComponent.source())) {
                        var tv = (TypeVariable) tpe.asType();
                        var realType = map.get(tv);
                        if (realType == null) {
                            var keys = new ArrayList<>(map.keySet());
                            for (var typeVariable : keys) {
                                var typeVariableElement = typeVariable.asElement();
                                if (tpe.getSimpleName().contentEquals(typeVariable.asElement().getSimpleName()) && typeVariableElement.getEnclosingElement().equals(tpe.getEnclosingElement())) {
                                    map.put(tv, map.get(typeVariable));
                                }
                            }
                        }
                    }
                    for (var methodParameterType : extensionComponent.dependencyTypes()) {
                        realParams.add(ComponentTemplateHelper.replace(types, methodParameterType, map));
                    }
                    declarations.add(new ComponentDeclaration.FromExtensionComponent(
                        realReturnType,
                        extensionComponent.source(),
                        realParams,
                        extensionComponent.dependencyTags(),
                        extensionComponent.tag(),
                        extensionComponent.generator()
                    ));
                }
                case ComponentDeclaration.PromisedProxyComponent promisedProxyComponent -> declarations.add(promisedProxyComponent.withType(realReturnType));
                default -> throw new IllegalArgumentException(sourceDeclaration.toString());
            }
        }
        if (declarations.isEmpty()) {
            return declarations;
        }
        if (declarations.size() == 1) {
            return declarations;
        }
        var exactMatch = declarations.stream().filter(d -> types.isSameType(
            d.type(),
            dependencyClaim.type()
        )).toList();
        if (!exactMatch.isEmpty()) {
            return exactMatch;
        }
        var nonDefault = declarations.stream().filter(Predicate.not(ComponentDeclaration::isDefault)).toList();
        if (!nonDefault.isEmpty()) {
            return nonDefault;
        }
        return declarations;
    }

    public static List<DeclarationWithIndex> findInterceptorDeclarations(ProcessingContext ctx, List<DeclarationWithIndex> sourceDeclarations, TypeMirror typeMirror) {
        var result = new ArrayList<DeclarationWithIndex>();
        for (var sourceDeclaration : sourceDeclarations) {
            if (sourceDeclaration.declaration().isInterceptor() && ctx.serviceTypeHelper.isInterceptorFor(sourceDeclaration.declaration().type(), typeMirror)) {
                result.add(sourceDeclaration);
            }
        }
        return result;
    }

    private static List<TypeParameterElement> collectTypeVariables(Element element) {
        var result = new ArrayList<TypeParameterElement>();
        element.accept(new ElementVisitor<Object, Object>() {
            @Override
            public Object visit(Element e, Object o) {
                return e.accept(this, o);
            }

            @Override
            public Object visitPackage(PackageElement e, Object o) {
                return null;
            }

            @Override
            public Object visitType(TypeElement e, Object o) {
                result.addAll(e.getTypeParameters());
                return null;
            }

            @Override
            public Object visitVariable(VariableElement e, Object o) {
                e.asType().accept(new TypeVisitor<Object, Object>() {
                    @Override
                    public Object visit(TypeMirror t, Object o) {
                        return null;
                    }

                    @Override
                    public Object visitPrimitive(PrimitiveType t, Object o) {
                        return null;
                    }

                    @Override
                    public Object visitNull(NullType t, Object o) {
                        return null;
                    }

                    @Override
                    public Object visitArray(ArrayType t, Object o) {
                        t.getComponentType().accept(this, o);
                        return null;
                    }

                    @Override
                    public Object visitDeclared(DeclaredType t, Object o) {
                        for (var typeArgument : t.getTypeArguments()) {
                            typeArgument.accept(this, o);
                        }
                        return null;
                    }

                    @Override
                    public Object visitError(ErrorType t, Object o) {
                        return null;
                    }

                    @Override
                    public Object visitTypeVariable(TypeVariable t, Object o) {
                        result.add((TypeParameterElement) t.asElement());
                        return null;
                    }

                    @Override
                    public Object visitWildcard(WildcardType t, Object o) {
                        return null;
                    }

                    @Override
                    public Object visitExecutable(ExecutableType t, Object o) {
                        return null;
                    }

                    @Override
                    public Object visitNoType(NoType t, Object o) {
                        return null;
                    }

                    @Override
                    public Object visitUnknown(TypeMirror t, Object o) {
                        return null;
                    }

                    @Override
                    public Object visitUnion(UnionType t, Object o) {
                        return null;
                    }

                    @Override
                    public Object visitIntersection(IntersectionType t, Object o) {
                        return null;
                    }
                }, null);
                return null;
            }

            @Override
            public Object visitExecutable(ExecutableElement e, Object o) {
                result.addAll(e.getTypeParameters());
                if (!e.getModifiers().contains(Modifier.STATIC)) {
                    e.getEnclosingElement().accept(this, o);
                }
                for (var parameter : e.getParameters()) {
                    parameter.accept(this, o);
                }

                return null;
            }

            @Override
            public Object visitTypeParameter(TypeParameterElement e, Object o) {
                result.add(e);
                return null;
            }

            @Override
            public Object visitUnknown(Element e, Object o) {
                return null;
            }
        }, null);
        return result;
    }

}
