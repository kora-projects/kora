package io.koraframework.validation.symbol.processor.testdata

import io.koraframework.validation.common.annotation.NotEmpty
import io.koraframework.validation.common.annotation.Size
import io.koraframework.validation.common.annotation.Valid

@Valid
class ValidBar {

    companion object {
        const val ignored: String = "ops"
    }

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
