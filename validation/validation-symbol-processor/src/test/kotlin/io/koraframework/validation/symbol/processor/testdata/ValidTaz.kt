package io.koraframework.validation.symbol.processor.testdata

import io.koraframework.validation.common.annotation.Pattern
import io.koraframework.validation.common.annotation.Valid

@Valid
data class ValidTaz(@Pattern("\\d+") val number: String) {

    companion object {
        const val ignored: String = "ops"
    }
}
