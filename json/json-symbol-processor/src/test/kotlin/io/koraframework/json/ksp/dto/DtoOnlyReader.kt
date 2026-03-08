package io.koraframework.json.ksp.dto

import io.koraframework.common.Mapping
import io.koraframework.json.common.annotation.JsonField
import io.koraframework.json.common.annotation.JsonReader
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken

@JsonReader
data class DtoOnlyReader(
    val field1: String,
    @JsonField("renamedField2") val field2: String,
    @field:Mapping(Field3Reader::class)
    val field3: Inner
) {
    class Field3Reader : io.koraframework.json.common.JsonReader<Inner?> {
        override fun read(parser: JsonParser): Inner {
            val token = parser.currentToken()
            if (token != JsonToken.VALUE_STRING) {
                throw RuntimeException()
            }
            val value = parser.valueAsString
            return Inner(value)
        }
    }

    data class Inner(val value: String)
}
