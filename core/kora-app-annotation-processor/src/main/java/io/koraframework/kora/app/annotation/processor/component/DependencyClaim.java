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

public record DependencyClaim(TypeMirror type, @Nullable String tag, DependencyClaimType claimType, @Nullable Element source) {
    public DependencyClaim {
        Objects.requireNonNull(type);
        Objects.requireNonNull(claimType);
        if (TypeParameterUtils.hasTypeParameter(type)) {
            if (source != null) {
                throw new ProcessingErrorException("""
                    Dependency uses an unresolved generic type:
                      type: %s

                    Kora dependency keys must be concrete types.

                    Fix:
                      - Bind the generic type parameter to a concrete type.
                      - Move generic construction to a component template or module method.
                    """.formatted(type).stripTrailing(), source);
            }
            throw new IllegalStateException("Kora internal error: generic dependency claim was created without source element: " + type);
        }
    }

    public DependencyClaim(TypeMirror type, @Nullable String tag, DependencyClaimType claimType) {
        this(type, tag, claimType, null);
    }

    public DependencyClaim(Element src, TypeMirror type, @Nullable String tag, DependencyClaimType claimType) {
        if (type.getKind() != TypeKind.DECLARED) {
            throw new ProcessingErrorException("""
                Dependency has non-reference type:
                  type: %s

                Kora graph components must be classes or interfaces.

                Fix:
                  - Use a reference type instead of a primitive or void type.
                  - Wrap primitive values in a class or boxed type.
                """.formatted(type).stripTrailing(), src);
        }

        this(type, tag, claimType, src);
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
        // todo nullable node of
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
