package ru.tinkoff.kora.kora.app.ksp.component

import com.google.devtools.ksp.symbol.KSType
import ru.tinkoff.kora.ksp.common.TagUtils.tagsMatch

data class DependencyClaim(val type: KSType, val tags: Set<String>, val claimType: DependencyClaimType) {
    fun tagsMatches(other: Collection<String>): Boolean {
        return tags.tagsMatch(other)
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
