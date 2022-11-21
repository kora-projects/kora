package ru.tinkoff.kora.json.ksp.dto

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import ru.tinkoff.kora.json.common.JsonReader
import ru.tinkoff.kora.json.common.JsonWriter
import ru.tinkoff.kora.json.common.annotation.Json
import ru.tinkoff.kora.json.common.annotation.JsonField
import java.io.IOException

@Json
data class DtoWithJsonFieldWriter(
    @JsonField("renamedField1") val field1: String,
    @JsonField("renamedField2") val field2: String,
    @JsonField(
        writer = CustomWriter::class,
        reader = CustomReader::class
    ) val field3: String?,
    @JsonField(
        writer = CustomWriter::class,
        reader = CustomReader::class
    ) val field4: String?
) {
    class CustomWriter : JsonWriter<String?> {
        @Throws(IOException::class)
        override fun write(gen: JsonGenerator, `object`: String?) {
            gen.writeNumber(-1)
        }
    }

    class CustomReader : JsonReader<String?> {
        @Throws(IOException::class)
        override fun read(parser: JsonParser): String? {
            val token = parser.currentToken()
            if (token == JsonToken.VALUE_NULL) {
                return null
            }
            if (token == JsonToken.VALUE_NUMBER_INT) {
                return parser.intValue.toString()
            }
            throw JsonParseException(parser, "expecting null or int, got $token")
        }
    }
}
