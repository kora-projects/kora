package ru.tinkoff.kora.kora.app.annotation.processor.component;

import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.annotation.processor.common.TypeParameterUtils;

import javax.lang.model.type.TypeMirror;
import java.util.Collection;
import java.util.Set;

public record DependencyClaim(TypeMirror type, Set<String> tags, DependencyClaimType claimType) {
    public DependencyClaim {
        if (TypeParameterUtils.hasTypeParameter(type)) {
            throw new IllegalStateException("Component can't have generic dependencies: " + type);
        }
    }

    public boolean tagsMatches(Collection<String> other) {
        if (this.tags.isEmpty() && other.isEmpty()) {
            return true;
        }
        if (this.tags.isEmpty()) {
            return false;
        }
        if (this.tags.contains(CommonClassNames.tagAny.canonicalName())) {
            return true;
        }
        for (var tag : this.tags) {
            if (!other.contains(tag)) {
                return false;
            }
        }
        return true;
    }

    public enum DependencyClaimType {
        ONE_REQUIRED,
        ONE_NULLABLE,
        VALUE_OF,
        PROMISE_OF,
        TYPE_REF,
        ALL_OF_ONE,
        ALL_OF_VALUE,
        ALL_OF_PROMISE
    }
}
