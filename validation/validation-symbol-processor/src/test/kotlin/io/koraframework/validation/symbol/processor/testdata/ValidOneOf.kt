package io.koraframework.validation.symbol.processor.testdata

import io.koraframework.validation.common.annotation.OneOf
import io.koraframework.validation.common.annotation.Valid

@Valid
data class ValidOneOf(@OneOf("NEW", "DONE") val status: String)
