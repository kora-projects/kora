package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.application.graph.ValueOf
import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.common.annotation.Root
import java.util.*

@KoraApp
interface AppWithOptionalComponents {
    fun presentInGraph(): PresentInGraph {
        return PresentInGraph()
    }

    @Root
    fun notEmptyOptionalParameter(param: Optional<PresentInGraph>): NotEmptyOptionalParameter {
        return NotEmptyOptionalParameter(param.orElse(null))
    }

    @Root
    fun emptyOptionalParameter(param: Optional<NotPresentInGraph>): EmptyOptionalParameter {
        return EmptyOptionalParameter(param.orElse(null))
    }

    @Root
    fun notEmptyValueOfOptional(param: ValueOf<Optional<PresentInGraph>>): NotEmptyValueOfOptional {
        return NotEmptyValueOfOptional(param.get().orElse(null))
    }

    @Root
    fun emptyValueOfOptional(param: ValueOf<Optional<NotPresentInGraph>>): EmptyValueOfOptional {
        return EmptyValueOfOptional(param.get().orElse(null))
    }

    @Root
    fun notEmptyNullable(param: PresentInGraph?): NotEmptyNullable {
        return NotEmptyNullable(param)
    }

    @Root
    fun emptyNullable(param: NotPresentInGraph?): EmptyNullable {
        return EmptyNullable(param)
    }

    class NotPresentInGraph
    class PresentInGraph
    data class NotEmptyOptionalParameter(val value: PresentInGraph?)

    data class EmptyOptionalParameter(val value: NotPresentInGraph?)

    data class NotEmptyValueOfOptional(val value: PresentInGraph?)

    data class EmptyValueOfOptional(val value: NotPresentInGraph?)

    data class NotEmptyNullable(val value: PresentInGraph?)

    data class EmptyNullable(val value: NotPresentInGraph?)
}
