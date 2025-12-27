package ru.tinkoff.kora.kora.app.annotation.processor.component;

import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.annotation.processor.common.*;
import ru.tinkoff.kora.kora.app.annotation.processor.ProcessingContext;
import ru.tinkoff.kora.kora.app.annotation.processor.component.DependencyClaim.DependencyClaimType;
import ru.tinkoff.kora.kora.app.annotation.processor.declaration.ComponentDeclaration;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;

public class ComponentDependencyHelper {

    public static List<DependencyClaim> parseDependencyClaims(ProcessingContext ctx, ComponentDeclaration componentDeclaration) {
        // TODO switch
        if (componentDeclaration instanceof ComponentDeclaration.FromModuleComponent moduleComponent) {
            var element = moduleComponent.method();
            var result = new ArrayList<DependencyClaim>(moduleComponent.methodParameterTypes().size() + 1);
            for (int i = 0; i < moduleComponent.methodParameterTypes().size(); i++) {
                var parameterType = moduleComponent.methodParameterTypes().get(i);
                var parameterElement = element.getParameters().get(i);
                var tags = TagUtils.parseTagValue(parameterElement);
                var isNullable = CommonUtils.isNullable(parameterElement);
                result.add(parseClaim(element, parameterType, tags, isNullable));
            }
            return result;
        } else if (componentDeclaration instanceof ComponentDeclaration.DiscoveredAsDependencyComponent discoveredAsDependency) {
            var element = discoveredAsDependency.constructor();
            var type = (ExecutableType) ctx.types.asMemberOf(discoveredAsDependency.type(), element);
            var result = new ArrayList<DependencyClaim>(element.getParameters().size());
            for (int i = 0; i < type.getParameterTypes().size(); i++) {
                var parameterType = type.getParameterTypes().get(i);
                var parameterElement = element.getParameters().get(i);
                var tags = TagUtils.parseTagValue(parameterElement);
                var isNullable = CommonUtils.isNullable(parameterElement);
                result.add(parseClaim(element, parameterType, tags, isNullable));
            }
            return result;
        } else if (componentDeclaration instanceof ComponentDeclaration.AnnotatedComponent annotated) {
            var element = annotated.constructor();
            var type = (ExecutableType) annotated.constructor().asType();
            var result = new ArrayList<DependencyClaim>(element.getParameters().size());
            for (int i = 0; i < annotated.methodParameterTypes().size(); i++) {
                var parameterType = annotated.methodParameterTypes().get(i);
                var parameterElement = element.getParameters().get(i);
                var tags = TagUtils.parseTagValue(parameterElement);
                var isNullable = CommonUtils.isNullable(parameterElement);
                result.add(parseClaim(element, parameterType, tags, isNullable));
            }
            return result;
        } else if (componentDeclaration instanceof ComponentDeclaration.FromExtensionComponent fromExtension) {
            var result = new ArrayList<DependencyClaim>(fromExtension.dependencyTypes().size() + 1);
            var executable = fromExtension.source().getKind() == ElementKind.METHOD || fromExtension.source().getKind() == ElementKind.CONSTRUCTOR
                ? (ExecutableElement) fromExtension.source()
                : null;
            for (int i = 0; i < fromExtension.dependencyTypes().size(); i++) {
                var parameterType = fromExtension.dependencyTypes().get(i);
                var tags = fromExtension.dependencyTags().get(i);
                var element = executable == null ? null : executable.getParameters().get(i);
                var isNullable = element != null && CommonUtils.isNullable(element);
                result.add(parseClaim(fromExtension.source(), parameterType, tags, isNullable));
            }
            return result;
        }
        throw new IllegalArgumentException();
    }

    public static DependencyClaim parseClaim(Element sourceElement, TypeMirror parameterType, @Nullable String tag, boolean isNullable) {
        if (TypeParameterUtils.hasRawTypes(parameterType)) {
            throw new ProcessingErrorException("Components with raw types can break dependency resolution in unpredictable way so they are forbidden", sourceElement);
        }

        var typeName = TypeName.get(parameterType);
        if (typeName instanceof ParameterizedTypeName ptn && parameterType instanceof DeclaredType dt) {
            if (ptn.rawType().canonicalName().equals(CommonClassNames.typeRef.canonicalName())) {
                return new DependencyClaim(dt.getTypeArguments().getFirst(), tag, DependencyClaimType.TYPE_REF);
            }
            if (ptn.rawType().canonicalName().equals(CommonClassNames.all.canonicalName())) {
                if (ptn.typeArguments().getFirst() instanceof ParameterizedTypeName allOfType && dt.getTypeArguments().getFirst() instanceof DeclaredType allOfTypeName) {
                    if (allOfType.rawType().canonicalName().equals(CommonClassNames.valueOf.canonicalName())) {
                        return new DependencyClaim(allOfTypeName.getTypeArguments().getFirst(), tag, DependencyClaimType.ALL_OF_VALUE);
                    }
                    if (allOfType.rawType().canonicalName().equals(CommonClassNames.promiseOf.canonicalName())) {
                        return new DependencyClaim(allOfTypeName.getTypeArguments().getFirst(), tag, DependencyClaimType.ALL_OF_PROMISE);
                    }
                }
                return new DependencyClaim(dt.getTypeArguments().getFirst(), tag, DependencyClaimType.ALL_OF_ONE);
            }
            if (ptn.rawType().canonicalName().equals(CommonClassNames.valueOf.canonicalName())) {
                if (isNullable) {
                    return new DependencyClaim(dt.getTypeArguments().getFirst(), tag, DependencyClaimType.NULLABLE_VALUE_OF);
                } else {
                    return new DependencyClaim(dt.getTypeArguments().getFirst(), tag, DependencyClaimType.VALUE_OF);
                }
            }
            if (ptn.rawType().canonicalName().equals(CommonClassNames.promiseOf.canonicalName())) {
                if (isNullable) {
                    return new DependencyClaim(dt.getTypeArguments().getFirst(), tag, DependencyClaimType.NULLABLE_PROMISE_OF);
                } else {
                    return new DependencyClaim(dt.getTypeArguments().getFirst(), tag, DependencyClaimType.PROMISE_OF);
                }
            }
        }
        if (isNullable) {
            return new DependencyClaim(parameterType, tag, DependencyClaimType.ONE_NULLABLE);
        } else {
            return new DependencyClaim(parameterType, tag, DependencyClaimType.ONE_REQUIRED);
        }
    }

}
