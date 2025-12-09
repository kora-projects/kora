package ru.tinkoff.kora.kora.app.annotation.processor.component;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.TagUtils;
import ru.tinkoff.kora.annotation.processor.common.TypeParameterUtils;

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
        ALL_OF_ONE,
        ALL_OF_VALUE,
        ALL_OF_PROMISE;

        public boolean isNullable() {
            return this == ONE_NULLABLE || this == NULLABLE_VALUE_OF || this == NULLABLE_PROMISE_OF;
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DependencyClaim[");
        sb.append("type=").append(type);
        sb.append(", claimType=").append(claimType);
        if (tag != null && !tag.isEmpty()) {
            sb.append(", tag=").append(tag);
        }
        sb.append(']');
        return sb.toString();
    }
}
