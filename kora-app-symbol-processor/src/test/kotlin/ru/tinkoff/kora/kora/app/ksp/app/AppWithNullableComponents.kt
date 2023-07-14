package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.common.annotation.Root

@KoraApp
interface AppWithNullableComponents {
    fun presentInGraph(): PresentInGraph {
        return PresentInGraph()
    }

    @Root
    fun notEmptyNullable(param: PresentInGraph?): NullableWithPresentValue {
        return NullableWithPresentValue(param)
    }

    @Root
    fun emptyNullable(param: NotPresentInGraph?): NullableWithMissingValue {
        return NullableWithMissingValue(param)
    }

    open class NotPresentInGraph
    class PresentInGraph

    data class NullableWithPresentValue(val value: PresentInGraph?)
    data class NullableWithMissingValue(val value: NotPresentInGraph?)
}
