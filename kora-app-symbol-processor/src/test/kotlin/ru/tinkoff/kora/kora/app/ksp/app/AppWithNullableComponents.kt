package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.annotation.processor.common.MockLifecycle

@KoraApp
interface AppWithNullableComponents {
    fun presentInGraph(): PresentInGraph {
        return PresentInGraph()
    }

    fun notEmptyNullable(param: PresentInGraph?): NullableWithPresentValue {
        return NullableWithPresentValue(param)
    }

    fun emptyNullable(param: NotPresentInGraph?): NullableWithMissingValue {
        return NullableWithMissingValue(param)
    }

    open class NotPresentInGraph : MockLifecycle
    class PresentInGraph : MockLifecycle

    data class NullableWithPresentValue(val value: PresentInGraph?) : MockLifecycle
    data class NullableWithMissingValue(val value: NotPresentInGraph?) : MockLifecycle
}
