package ru.tinkoff.kora.kora.app.ksp.app

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle
import ru.tinkoff.kora.application.graph.ValueOf
import ru.tinkoff.kora.common.KoraApp
import java.util.*

@KoraApp
interface AppWithOptionalComponents {
    fun presentInGraph(): PresentInGraph {
        return PresentInGraph()
    }

    fun notEmptyOptionalParameter(param: Optional<PresentInGraph>): NotEmptyOptionalParameter {
        return NotEmptyOptionalParameter(param.orElse(null))
    }

    fun emptyOptionalParameter(param: Optional<NotPresentInGraph>): EmptyOptionalParameter {
        return EmptyOptionalParameter(param.orElse(null))
    }

    fun notEmptyValueOfOptional(param: ValueOf<Optional<PresentInGraph>>): NotEmptyValueOfOptional {
        return NotEmptyValueOfOptional(param.get().orElse(null))
    }

    fun emptyValueOfOptional(param: ValueOf<Optional<NotPresentInGraph>>): EmptyValueOfOptional {
        return EmptyValueOfOptional(param.get().orElse(null))
    }

    fun notEmptyNullable(param: PresentInGraph?): NotEmptyNullable {
        return NotEmptyNullable(param)
    }

    fun emptyNullable(param: NotPresentInGraph?): EmptyNullable {
        return EmptyNullable(param)
    }

    class NotPresentInGraph : MockLifecycle
    class PresentInGraph : MockLifecycle
    data class NotEmptyOptionalParameter(val value: PresentInGraph?) : MockLifecycle

    data class EmptyOptionalParameter(val value: NotPresentInGraph?) : MockLifecycle

    data class NotEmptyValueOfOptional(val value: PresentInGraph?) : MockLifecycle

    data class EmptyValueOfOptional(val value: NotPresentInGraph?) : MockLifecycle

    data class NotEmptyNullable(val value: PresentInGraph?) : MockLifecycle

    data class EmptyNullable(val value: NotPresentInGraph?) : MockLifecycle
}
