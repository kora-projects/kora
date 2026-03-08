package io.koraframework.json.ksp.dto

import io.koraframework.json.common.annotation.JsonField
import io.koraframework.json.common.annotation.JsonReader

@JsonReader
data class DtoWithNullableFields(
    @JsonField("field_1") val field1: String,
    val field4: Int,
    val field2: String?,
    val field3: String?
)
