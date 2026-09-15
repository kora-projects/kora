package io.koraframework.kora.app.ksp.component

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ksp.toTypeName
import io.koraframework.ksp.common.TagUtils.tagMatches

data class DependencyClaim(val type: KSType, val tag: String?, val claimType: DependencyClaimType, val source: KSAnnotated? = null) {

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
        ALL_OF_PROMISE,

        GRAPH,

        // todo nullable node of
        NODE_OF,
        ;

        /**
         * @return true if a claim of this type can be satisfied by a single promised proxy component,
         * which is how the graph builder breaks dependency cycles
         */
        fun isProxyable(): Boolean = when (this) {
            ONE_REQUIRED, NULLABLE_ONE, VALUE_OF, NULLABLE_VALUE_OF, PROMISE_OF, NULLABLE_PROMISE_OF, NODE_OF -> true
            TYPE_REF, ALL, ALL_OF_VALUE, ALL_OF_PROMISE, GRAPH -> false
        }
    }

    fun tagMatches(other: String?) = tag.tagMatches(other)

    override fun toString(): String {
        return "DependencyClaim(type=${type.toTypeName()}, tag=$tag, claimType=$claimType)"
    }
}
