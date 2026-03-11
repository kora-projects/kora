package io.koraframework.json.ksp.dto

import io.koraframework.common.Mapping
import io.koraframework.json.common.JsonReader
import io.koraframework.json.common.JsonWriter
import io.koraframework.json.common.annotation.Json
import io.koraframework.json.common.annotation.JsonField
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.core.exc.StreamReadException
import java.io.IOException

@Json
data class DtoWithJsonFieldWriter(
    @JsonField("renamedField1") val field1: String,
    @JsonField("renamedField2") val field2: String,
    @Mapping(CustomWriter::class)
    @Mapping(CustomReader::class)
    val field3: String?,
    @Mapping(CustomWriter::class)
    @Mapping(CustomReader::class)
    val field4: String?
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
            throw StreamReadException(parser, "expecting null or int, got $token")
        }
    }
}
