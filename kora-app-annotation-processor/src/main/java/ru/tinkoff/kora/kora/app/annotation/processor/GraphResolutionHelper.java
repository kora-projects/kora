package ru.tinkoff.kora.kora.app.annotation.processor;

import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.annotation.processor.common.TagUtils;
import ru.tinkoff.kora.kora.app.annotation.processor.component.ComponentDependency;
import ru.tinkoff.kora.kora.app.annotation.processor.component.DependencyClaim;
import ru.tinkoff.kora.kora.app.annotation.processor.component.ResolvedComponent;
import ru.tinkoff.kora.kora.app.annotation.processor.declaration.ComponentDeclaration;

import javax.annotation.Nullable;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static ru.tinkoff.kora.kora.app.annotation.processor.component.DependencyClaim.DependencyClaimType.*;

public class GraphResolutionHelper {
    @Nullable
    public static ComponentDependency.SingleDependency findDependency(ProcessingContext ctx, ComponentDeclaration forDeclaration, List<ResolvedComponent> resolvedComponents, DependencyClaim dependencyClaim) {
        var dependencies = findDependencies(ctx, resolvedComponents, dependencyClaim);
        if (dependencies.size() == 1) {
            return dependencies.get(0);
        }
        if (dependencies.isEmpty()) {
            return null;
        }
        var deps = dependencies.stream().map(ComponentDependency.SingleDependency::component).map(Objects::toString).collect(Collectors.joining("\n")).indent(2);
        throw new ProcessingErrorException("More than one component matches dependency claim " + dependencyClaim.type() + ":\n" + deps, forDeclaration.source());
    }

    public static List<ComponentDependency.SingleDependency> findDependencies(ProcessingContext ctx, List<ResolvedComponent> resolvedComponents, DependencyClaim dependencyClaim) {
        var result = new ArrayList<ComponentDependency.SingleDependency>(4);
        for (var resolvedComponent : resolvedComponents) {
            if (!dependencyClaim.tagsMatches(resolvedComponent.tags())) {
                continue;
            }
            var isDirectAssignable = ctx.types.isAssignable(resolvedComponent.type(), dependencyClaim.type());
            var isWrappedAssignable = ctx.serviceTypeHelper.isAssignableToUnwrapped(resolvedComponent.type(), dependencyClaim.type());
            if (!isDirectAssignable && !isWrappedAssignable) {
                continue;
            }
            var targetDependency = isWrappedAssignable
                ? new ComponentDependency.WrappedTargetDependency(dependencyClaim, resolvedComponent)
                : new ComponentDependency.TargetDependency(dependencyClaim, resolvedComponent);
            switch (dependencyClaim.claimType()) {
                case ONE_REQUIRED, ONE_NULLABLE -> result.add(targetDependency);
                case PROMISE_OF, NULLABLE_PROMISE_OF -> result.add(new ComponentDependency.PromiseOfDependency(dependencyClaim, targetDependency));
                case VALUE_OF, NULLABLE_VALUE_OF -> result.add(new ComponentDependency.ValueOfDependency(dependencyClaim, targetDependency));
                case ALL_OF_ONE, ALL_OF_PROMISE, ALL_OF_VALUE, TYPE_REF -> throw new IllegalStateException();
            }
            ;
        }
        return result;
    }

    @Nullable
    public static ComponentDeclaration findFinalDependency(DependencyClaim dependencyClaim) {
        if (dependencyClaim.type().getKind() != TypeKind.DECLARED) {
            return null;
        }
        var declaredType = (DeclaredType) dependencyClaim.type();
        var element = (TypeElement) declaredType.asElement();
        if (element.getKind() != ElementKind.CLASS) {
            return null;
        }
        if (!element.getModifiers().contains(Modifier.FINAL) || !element.getModifiers().contains(Modifier.PUBLIC)) {
            return null;
        }
        var tags = TagUtils.parseTagValue(element);
        if (dependencyClaim.tagsMatches(tags)) {
            return ComponentDeclaration.fromDependency(element);
        } else {
            return null;
        }
    }

    public static List<ComponentDependency.SingleDependency> findDependenciesForAllOf(ProcessingContext ctx, DependencyClaim dependencyClaim, List<ResolvedComponent> resolvedComponents) {
        var claimType = dependencyClaim.claimType();
        var result = new ArrayList<ComponentDependency.SingleDependency>();
        components:
        for (var component : resolvedComponents) {
            if (!dependencyClaim.tagsMatches(component.tags())) {
                continue components;
            }
            if (ctx.types.isAssignable(component.type(), dependencyClaim.type())) {
                var targetDependency = new ComponentDependency.TargetDependency(dependencyClaim, component);
                ComponentDependency.SingleDependency dependency;
                // todo switch
                if (claimType == ALL_OF_ONE) {
                    dependency = targetDependency;
                } else if (claimType == ALL_OF_PROMISE) {
                    dependency = new ComponentDependency.PromiseOfDependency(dependencyClaim, targetDependency);
                } else if (claimType == ALL_OF_VALUE) {
                    dependency = new ComponentDependency.ValueOfDependency(dependencyClaim, targetDependency);
                } else {
                    throw new IllegalStateException("Unexpected value: " + dependencyClaim.claimType());
                }
                result.add(dependency);
            }
            if (ctx.serviceTypeHelper.isAssignableToUnwrapped(component.type(), dependencyClaim.type())) {
                var targetDependency = new ComponentDependency.WrappedTargetDependency(dependencyClaim, component);
                ComponentDependency.SingleDependency dependency;
                if (claimType == ALL_OF_ONE) {
                    dependency = targetDependency;
                } else if (claimType == ALL_OF_PROMISE) {
                    dependency = new ComponentDependency.PromiseOfDependency(dependencyClaim, targetDependency);
                } else if (claimType == ALL_OF_VALUE) {
                    dependency = new ComponentDependency.ValueOfDependency(dependencyClaim, targetDependency);
                } else {
                    throw new IllegalStateException("Unexpected value: " + dependencyClaim.claimType());
                }
                result.add(dependency);
            }
        }
        return result;
    }

    @Nullable
    public static ComponentDeclaration findDependencyDeclarationFromTemplate(ProcessingContext ctx, ComponentDeclaration forDeclaration, List<ComponentDeclaration> sourceDeclarations, DependencyClaim dependencyClaim) {
        var declarations = findDependencyDeclarationsFromTemplate(ctx, forDeclaration, sourceDeclarations, dependencyClaim);
        if (declarations.size() == 0) {
            return null;
        }
        if (declarations.size() == 1) {
            return declarations.get(0);
        }
        var exactMatch = declarations.stream().filter(d -> ctx.types.isSameType(
            d.type(),
            dependencyClaim.type()
        )).toList();
        if (exactMatch.size() == 1) {
            return exactMatch.get(0);
        }
        var deps = declarations.stream().map(Objects::toString).collect(Collectors.joining("\n")).indent(2);
        throw new ProcessingErrorException("More than one component matches dependency claim " + dependencyClaim.type() + ":\n" + deps, forDeclaration.source());
    }

    public static List<ComponentDeclaration> findDependencyDeclarationsFromTemplate(ProcessingContext ctx, ComponentDeclaration forDeclaration, List<ComponentDeclaration> sourceDeclarations, DependencyClaim dependencyClaim) {
        var claimType = dependencyClaim.claimType();
        if (claimType == ALL_OF_ONE || claimType == ALL_OF_PROMISE || claimType == ALL_OF_VALUE) {
            throw new IllegalStateException();
        }
        var types = ctx.types;
        var declarations = new ArrayList<ComponentDeclaration>();
        sources:
        for (var sourceDeclaration : sourceDeclarations) {
            if (!sourceDeclaration.isTemplate()) {
                continue;
            }
            if (!dependencyClaim.tagsMatches(sourceDeclaration.tags())) {
                continue sources;
            }
            var requiredDeclaredType = (DeclaredType) dependencyClaim.type();
            var declarationDeclaredType = (DeclaredType) sourceDeclaration.type();
            var match = ComponentTemplateHelper.match(ctx, declarationDeclaredType, requiredDeclaredType);
            if (match instanceof ComponentTemplateHelper.TemplateMatch.None) {
                continue sources;
            }
            if (!(match instanceof ComponentTemplateHelper.TemplateMatch.Some some)) {
                throw new IllegalStateException();
            }
            var map = some.map();
            var realReturnType = ComponentTemplateHelper.replace(types, declarationDeclaredType, map);

            // todo switch
            if (sourceDeclaration instanceof ComponentDeclaration.FromModuleComponent declaredComponent) {
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
                    declaredComponent.tags(),
                    declaredComponent.method(),
                    realParams,
                    typeParameters
                ));
            } else if (sourceDeclaration instanceof ComponentDeclaration.AnnotatedComponent annotatedComponent) {
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
                    annotatedComponent.tags(),
                    annotatedComponent.constructor(),
                    realParams,
                    typeParameters
                ));
            } else if (sourceDeclaration instanceof ComponentDeclaration.FromExtensionComponent extensionComponent) {
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
                for (var methodParameterType : extensionComponent.methodParameterTypes()) {
                    realParams.add(ComponentTemplateHelper.replace(types, methodParameterType, map));
                }
                declarations.add(new ComponentDeclaration.FromExtensionComponent(
                    realReturnType,
                    extensionComponent.sourceMethod(),
                    realParams
                ));
            } else if (sourceDeclaration instanceof ComponentDeclaration.PromisedProxyComponent promisedProxyComponent) {
                declarations.add(promisedProxyComponent.withType(realReturnType));
            } else if (sourceDeclaration instanceof ComponentDeclaration.DiscoveredAsDependencyComponent) {
                throw new IllegalStateException();
            } else {
                throw new IllegalArgumentException(sourceDeclaration.toString());
            }
        }
        if (declarations.size() == 0) {
            return declarations;
        }
        if (declarations.size() == 1) {
            return declarations;
        }
        var exactMatch = declarations.stream().filter(d -> types.isSameType(
            d.type(),
            dependencyClaim.type()
        )).toList();
        if (exactMatch.isEmpty()) {
            return declarations;
        } else {
            return exactMatch;
        }
    }

    @Nullable
    public static ComponentDeclaration findDependencyDeclaration(ProcessingContext ctx, ComponentDeclaration forDeclaration, List<ComponentDeclaration> sourceDeclarations, DependencyClaim dependencyClaim) {
        if (dependencyClaim.claimType() == ALL_OF_ONE || dependencyClaim.claimType() == ALL_OF_PROMISE || dependencyClaim.claimType() == ALL_OF_VALUE) {
            throw new IllegalStateException();
        }
        var declarations = new ArrayList<ComponentDeclaration>();
        for (var sourceDeclaration : sourceDeclarations) {
            if (!dependencyClaim.tagsMatches(sourceDeclaration.tags())) {
                continue;
            }
            var isDirectAssignable = ctx.types.isAssignable(sourceDeclaration.type(), dependencyClaim.type());
            var isAssignable = isDirectAssignable || ctx.serviceTypeHelper.isAssignableToUnwrapped(sourceDeclaration.type(), dependencyClaim.type());

            if (isAssignable) {
                declarations.add(sourceDeclaration);
            }
        }
        if (declarations.size() == 1) {
            return declarations.get(0);
        }
        if (declarations.isEmpty()) {
            return null;
        }
        var exactMatch = declarations.stream()
            .filter(d -> ctx.types.isSameType(d.type(), dependencyClaim.type()) || ctx.serviceTypeHelper.isSameToUnwrapped(d.type(), dependencyClaim.type()))
            .toList();
        if (exactMatch.size() == 1) {
            return exactMatch.get(0);
        }
        var nonDefaultComponents = declarations.stream()
            .filter(Predicate.not(ComponentDeclaration::isDefault))
            .toList();
        if (nonDefaultComponents.size() == 1) {
            return nonDefaultComponents.get(0);
        }

        var deps = declarations.stream().map(Objects::toString).collect(Collectors.joining("\n")).indent(2);
        throw new ProcessingErrorException("More than one component matches dependency claim " + dependencyClaim.type() + ":\n" + deps, forDeclaration.source());
    }

    public static ArrayList<ComponentDeclaration> findDependencyDeclarations(ProcessingContext ctx, List<ComponentDeclaration> sourceDeclarations, DependencyClaim dependencyClaim) {
        var result = new ArrayList<ComponentDeclaration>();
        for (var sourceDeclaration : sourceDeclarations) {
            if (sourceDeclaration.isTemplate()) {
                continue;
            }
            if (!dependencyClaim.tagsMatches(sourceDeclaration.tags())) {
                continue;
            }
            if (ctx.types.isAssignable(sourceDeclaration.type(), dependencyClaim.type()) || ctx.serviceTypeHelper.isAssignableToUnwrapped(sourceDeclaration.type(), dependencyClaim.type())) {
                result.add(sourceDeclaration);
            }
        }
        return result;
    }

    public static List<ComponentDeclaration> findInterceptorDeclarations(ProcessingContext ctx, List<ComponentDeclaration> sourceDeclarations, TypeMirror typeMirror) {
        var result = new ArrayList<ComponentDeclaration>();
        for (var sourceDeclaration : sourceDeclarations) {
            if (ctx.serviceTypeHelper.isInterceptorFor(sourceDeclaration.type(), typeMirror)) {
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
