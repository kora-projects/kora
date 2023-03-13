package ru.tinkoff.kora.validation.symbol.processor.testdata

import ru.tinkoff.kora.validation.common.annotation.NotEmpty
import ru.tinkoff.kora.validation.common.annotation.Size
import ru.tinkoff.kora.validation.common.annotation.Valid

@Valid
class ValidBar {

    @NotEmpty
    var id: String? = null
        get() = field
        set(value) {
            field = value
        }

    @Size(min = 1, max = 5)
    var codes: List<Int> = emptyList()

    @Valid
    var tazs: List<ValidTaz> = emptyList()
}
