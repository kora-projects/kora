package ru.tinkoff.kora.kora.app.annotation.processor;

import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.annotation.processor.common.NameUtils;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.Objects;

public class ServiceTypesHelper {
    private final Elements elements;
    private final Types types;
    private final TypeElement wrappedTypeElement;
    private final DeclaredType wrappedType;
    private final TypeElement interceptorTypeElement;
    private final DeclaredType interceptorType;

    public ServiceTypesHelper(Elements elements, Types types) {
        this.elements = elements;
        this.types = types;
        this.wrappedTypeElement = Objects.requireNonNull(this.elements.getTypeElement(CommonClassNames.wrapped.canonicalName()));
        this.wrappedType = Objects.requireNonNull(this.types.getDeclaredType(this.wrappedTypeElement, this.types.getWildcardType(null, null)));
        this.interceptorTypeElement = Objects.requireNonNull(this.elements.getTypeElement(CommonClassNames.graphInterceptor.canonicalName()));
        this.interceptorType = Objects.requireNonNull(this.types.getDeclaredType(this.interceptorTypeElement, this.types.getWildcardType(null, null)));
    }

    public boolean isAssignableToUnwrapped(TypeMirror maybeWrapped, TypeMirror typeMirror) {
        if (!this.types.isAssignable(maybeWrapped, this.wrappedType)) {
            return false;
        }
        var wrappedParameterElement = this.wrappedTypeElement.getTypeParameters().get(0); // somehow it can be changed during execution
        var declaredType = (DeclaredType) maybeWrapped;
        var unwrappedType = this.types.asMemberOf(declaredType, wrappedParameterElement);
        return this.types.isAssignable(unwrappedType, typeMirror);
    }

    public boolean isSameToUnwrapped(TypeMirror maybeWrapped, TypeMirror typeMirror) {
        if (!this.types.isAssignable(maybeWrapped, this.wrappedType)) {
            return false;
        }
        var wrappedParameterElement = this.wrappedTypeElement.getTypeParameters().get(0); // somehow it can be changed during execution
        var declaredType = (DeclaredType) maybeWrapped;
        var unwrappedType = this.types.asMemberOf(declaredType, wrappedParameterElement);
        return this.types.isSameType(unwrappedType, typeMirror);
    }

    public boolean isInterceptorFor(TypeMirror interceptorType, TypeMirror targetType) {
        if (!this.types.isAssignable(interceptorType, this.interceptorType)) {
            return false;
        }
        var interceptorTypeParameter = this.interceptorTypeElement.getTypeParameters().get(0); // somehow it can be changed during execution
        var declaredType = (DeclaredType) interceptorType;
        var interceptedType = this.types.asMemberOf(declaredType, interceptorTypeParameter);
        return isInterceptable(interceptedType, targetType);
    }

    public boolean isInterceptable(TypeMirror interceptedType, TypeMirror targetType) {
        if (this.types.isSameType(interceptedType, targetType)) {
            return true;
        } else if (this.types.isAssignable(targetType, interceptedType)) {
            // Check if is AopProxy
            var typeMirrorElement = types.asElement(targetType);
            var annotation = AnnotationUtils.findAnnotation(typeMirrorElement, CommonClassNames.aopProxy);
            if (annotation != null) {
                var interceptedTypeElement = types.asElement(interceptedType);
                var aopProxyName = NameUtils.generatedType(interceptedTypeElement, "_AopProxy");
                var expectedAopProxyCanonicalName = elements.getPackageOf(interceptedTypeElement).toString() + "." + aopProxyName;
                return expectedAopProxyCanonicalName.equals(typeMirrorElement.toString());
            }
        }

        return false;
    }

    public boolean isInterceptor(TypeMirror maybeInterceptor) {
        return this.types.isAssignable(maybeInterceptor, this.interceptorType);
    }

    public TypeMirror getInterceptedType(TypeMirror maybeInterceptor) {
        var interceptorTypeParameter = this.interceptorTypeElement.getTypeParameters().get(0); // somehow it can be changed during execution
        var declaredType = (DeclaredType) maybeInterceptor;
        return this.types.asMemberOf(declaredType, interceptorTypeParameter);
    }
}
