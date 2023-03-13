package ru.tinkoff.kora.validation.symbol.processor.testdata

import ru.tinkoff.kora.validation.common.annotation.Pattern
import ru.tinkoff.kora.validation.common.annotation.Valid

@Valid
data class ValidTaz(@Pattern("\\d+") val number: String)
