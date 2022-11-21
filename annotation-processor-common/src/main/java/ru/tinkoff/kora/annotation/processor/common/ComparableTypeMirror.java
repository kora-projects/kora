package ru.tinkoff.kora.annotation.processor.common;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.util.Types;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Objects;

public record ComparableTypeMirror(Types types, TypeMirror typeMirror) implements TypeMirror {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof ComparableTypeMirror that) {
            return this.types.isSameType(this.typeMirror, that.typeMirror);
        }
        if (o instanceof TypeMirror typeMirror) {
            return this.types.isSameType(this.typeMirror, typeMirror);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.typeMirror.toString());
    }

    @Override
    public String toString() {
        return this.typeMirror.toString();
    }

    @Override
    public TypeKind getKind() {
        return this.typeMirror.getKind();
    }

    @Override
    public List<? extends AnnotationMirror> getAnnotationMirrors() {
        return this.typeMirror.getAnnotationMirrors();
    }

    @Override
    public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
        return this.typeMirror.getAnnotation(annotationType);
    }

    @Override
    public <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationType) {
        return this.typeMirror.getAnnotationsByType(annotationType);
    }

    @Override
    public <R, P> R accept(TypeVisitor<R, P> v, P p) {
        return this.typeMirror.accept(v, p);
    }
}
