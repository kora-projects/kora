package io.koraframework.kora.app.annotation.processor.component;

import com.palantir.javapoet.TypeName;
import io.koraframework.annotation.processor.common.ProcessingErrorException;
import io.koraframework.annotation.processor.common.TagUtils;
import io.koraframework.annotation.processor.common.TypeParameterUtils;
import org.jspecify.annotations.Nullable;

import javax.lang.model.element.Element;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.Objects;

public record DependencyClaim(TypeMirror type, @Nullable String tag, DependencyClaimType claimType) {
    public DependencyClaim {
        Objects.requireNonNull(type);
        Objects.requireNonNull(claimType);
        if (TypeParameterUtils.hasTypeParameter(type)) {
            throw new IllegalStateException("Component can't have generic dependencies: " + type);
        }
    }

    public DependencyClaim(Element src, TypeMirror type, @Nullable String tag, DependencyClaimType claimType) {
        if (type.getKind() != TypeKind.DECLARED) {
            throw new ProcessingErrorException("Only reference types are allowed as graph components, got " + type + " at " + src, src);
        }

        this(type, tag, claimType);
    }

    public boolean tagsMatches(String other) {
        return TagUtils.tagsMatch(this.tag, other);
    }

    public enum DependencyClaimType {
        ONE_REQUIRED,
        ONE_NULLABLE,
        VALUE_OF,
        NULLABLE_VALUE_OF,
        PROMISE_OF,
        NULLABLE_PROMISE_OF,
        TYPE_REF,
        GRAPH,
        NODE_OF,
        ALL_OF_ONE,
        ALL_OF_VALUE,
        ALL_OF_PROMISE;

        public boolean isNullable() {
            return this == ONE_NULLABLE || this == NULLABLE_VALUE_OF || this == NULLABLE_PROMISE_OF;
        }
    }

    @Override
    public String toString() {
        return "DependencyClaim{type=" + TypeName.get(type)
            + ", tag=" + tag
            + ", claimType=" + claimType + '}';
    }
}
