package ru.tinkoff.kora.json.ksp.dto

import ru.tinkoff.kora.json.common.annotation.JsonField
import ru.tinkoff.kora.json.common.annotation.JsonReader
import java.util.*

@JsonReader
data class DtoWithNullableFields(
    @JsonField("field_1") val field1: String,
    val field4: Int,
    val field2: String?,
    val field3: String?
)
