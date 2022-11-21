package ru.tinkoff.kora.annotation.processor.common;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.*;
import java.lang.annotation.Annotation;
import java.util.List;

public class GenericMethod implements ExecutableType {
    private final ExecutableElement element;
    private final TypeMirror returnType;
    private final List<? extends TypeMirror> parameterTypes;
    private final TypeMirror receiverType;
    private final List<? extends TypeMirror> thrownTypes;

    public GenericMethod(ExecutableElement element, TypeMirror returnType, List<? extends TypeMirror> parameterTypes, TypeMirror receiverType, List<? extends TypeMirror> thrownTypes) {
        this.element = element;
        this.returnType = returnType;
        this.parameterTypes = parameterTypes;
        this.receiverType = receiverType;
        this.thrownTypes = thrownTypes;
    }

    @Override
    public List<? extends TypeVariable> getTypeVariables() {
        return this.element.getTypeParameters().stream().map(TypeParameterElement::asType).map(TypeVariable.class::cast).toList();
    }

    @Override
    public TypeMirror getReturnType() {
        return this.returnType;
    }

    @Override
    public List<? extends TypeMirror> getParameterTypes() {
        return this.parameterTypes;
    }

    @Override
    public TypeMirror getReceiverType() {
        return this.receiverType;
    }

    @Override
    public List<? extends TypeMirror> getThrownTypes() {
        return this.thrownTypes;
    }

    @Override
    public TypeKind getKind() {
        return TypeKind.EXECUTABLE;
    }

    @Override
    public List<? extends AnnotationMirror> getAnnotationMirrors() {
        return this.element.getAnnotationMirrors();
    }

    @Override
    public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
        return this.element.getAnnotation(annotationType);
    }

    @Override
    public <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationType) {
        return this.element.getAnnotationsByType(annotationType);
    }

    @Override
    public <R, P> R accept(TypeVisitor<R, P> v, P p) {
        return v.visitExecutable(this, p);
    }
}
