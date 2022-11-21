package ru.tinkoff.kora.json.ksp.dto

import ru.tinkoff.kora.json.common.annotation.Json

@Json
data class DtoWithInnerDto(
    val inner: InnerDto?,
    val field2: List<InnerDto>,
    val field3: Map<String, InnerDto>,
    val field4: List<List<InnerDto>>
) {
    @Json
    data class InnerDto(val field1: String)
}
