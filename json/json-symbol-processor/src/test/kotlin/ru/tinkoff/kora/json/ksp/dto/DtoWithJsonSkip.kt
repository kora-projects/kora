package ru.tinkoff.kora.json.ksp.dto

import ru.tinkoff.kora.json.common.annotation.Json
import ru.tinkoff.kora.json.common.annotation.JsonSkip

@Json
data class DtoWithJsonSkip(
    val field1: String,
    val field2: String,
    @JsonSkip val field3: String,
    @JsonSkip val field4: String
)
