package ru.tinkoff.kora.kora.app.annotation.processor;

import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;

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
    private final TypeMirror lifecycleType;

    public ServiceTypesHelper(Elements elements, Types types) {
        this.elements = elements;
        this.types = types;
        this.wrappedTypeElement = Objects.requireNonNull(this.elements.getTypeElement(CommonClassNames.wrapped.canonicalName()));
        this.wrappedType = Objects.requireNonNull(this.types.getDeclaredType(this.wrappedTypeElement, this.types.getWildcardType(null, null)));
        this.interceptorTypeElement = Objects.requireNonNull(this.elements.getTypeElement(CommonClassNames.graphInterceptor.canonicalName()));
        this.interceptorType = Objects.requireNonNull(this.types.getDeclaredType(this.interceptorTypeElement, this.types.getWildcardType(null, null)));
        var lifecycleTypeElement = Objects.requireNonNull(this.elements.getTypeElement(CommonClassNames.lifecycle.canonicalName()));
        this.lifecycleType = Objects.requireNonNull(lifecycleTypeElement.asType());
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

    public boolean isInterceptorFor(TypeMirror maybeInterceptor, TypeMirror typeMirror) {
        if (!this.types.isAssignable(maybeInterceptor, this.interceptorType)) {
            return false;
        }
        var interceptorTypeParameter = this.interceptorTypeElement.getTypeParameters().get(0); // somehow it can be changed during execution
        var declaredType = (DeclaredType) maybeInterceptor;
        var interceptedType = this.types.asMemberOf(declaredType, interceptorTypeParameter);
        return this.types.isSameType(interceptedType, typeMirror);
    }

    public boolean isInterceptor(TypeMirror maybeInterceptor) {
        return this.types.isAssignable(maybeInterceptor, this.interceptorType);
    }

    public TypeMirror getInterceptedType(TypeMirror maybeInterceptor) {
        var interceptorTypeParameter = this.interceptorTypeElement.getTypeParameters().get(0); // somehow it can be changed during execution
        var declaredType = (DeclaredType) maybeInterceptor;
        return this.types.asMemberOf(declaredType, interceptorTypeParameter);
    }

    public boolean isLifecycle(TypeMirror type) {
        return this.types.isAssignable(type, this.lifecycleType) || this.isAssignableToUnwrapped(type, this.lifecycleType);
    }
}
