package ru.tinkoff.kora.json.ksp.dto

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import ru.tinkoff.kora.json.common.annotation.JsonField
import ru.tinkoff.kora.json.common.annotation.JsonReader

@JsonReader
data class DtoOnlyReader(
    val field1: String,
    @JsonField("renamedField2") val field2: String,
    @JsonField( reader = Field3Reader::class) val field3: Inner
) {
    class Field3Reader : ru.tinkoff.kora.json.common.JsonReader<Inner?> {
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
