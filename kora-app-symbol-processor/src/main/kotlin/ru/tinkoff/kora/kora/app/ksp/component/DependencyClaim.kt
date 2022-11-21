package ru.tinkoff.kora.kora.app.ksp.component

import com.google.devtools.ksp.symbol.KSType
import ru.tinkoff.kora.ksp.common.CommonClassNames

data class DependencyClaim(val type: KSType, val tags: Set<String>, val claimType: DependencyClaimType) {
    fun tagsMatches(other: Collection<String?>): Boolean {
        if (tags.isEmpty() && other.isEmpty()) {
            return true
        }
        if (tags.isEmpty()) {
            return false
        }
        if (tags.contains(CommonClassNames.tagAny.canonicalName)) {
            return true
        }
        for (tag in tags) {
            if (!other.contains(tag)) {
                return false
            }
        }
        return true
    }

    enum class DependencyClaimType {
        ONE_REQUIRED,
        NULLABLE_ONE,
        VALUE_OF,
        NULLABLE_VALUE_OF,
        PROMISE_OF,
        NULLABLE_PROMISE_OF,
        TYPE_REF,
        ALL,
        ALL_OF_VALUE,
        ALL_OF_PROMISE
    }

}
