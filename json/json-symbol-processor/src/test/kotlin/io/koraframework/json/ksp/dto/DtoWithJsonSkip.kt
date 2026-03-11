package io.koraframework.json.ksp.dto

import io.koraframework.json.common.annotation.Json
import io.koraframework.json.common.annotation.JsonSkip

@Json
data class DtoWithJsonSkip(
    val field1: String,
    val field2: String,
    @JsonSkip val field3: String,
    @JsonSkip val field4: String
)
