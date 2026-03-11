package io.koraframework.json.ksp.dto

import io.koraframework.common.Mapping
import io.koraframework.json.common.annotation.JsonField
import io.koraframework.json.common.annotation.JsonSkip
import io.koraframework.json.common.annotation.JsonWriter
import tools.jackson.core.JsonGenerator

@JsonWriter
data class DtoOnlyWriter(
    val field1: String,
    @JsonField("renamedField2") val field2: String,
    @field:Mapping(FieldWriter::class) val field3: Inner,
    @JsonSkip val field4: String
) {
    class FieldWriter : io.koraframework.json.common.JsonWriter<Inner?> {
        override fun write(gen: JsonGenerator, obj: Inner?) {
            if (obj == null) {
                gen.writeNull()
            } else {
                gen.writeString(obj.value)
            }
        }
    }

    data class Inner(val value: String)
}
