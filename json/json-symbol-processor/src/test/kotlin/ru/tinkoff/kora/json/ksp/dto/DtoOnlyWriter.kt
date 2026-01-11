package ru.tinkoff.kora.json.ksp.dto

import ru.tinkoff.kora.common.Mapping
import ru.tinkoff.kora.json.common.annotation.JsonField
import ru.tinkoff.kora.json.common.annotation.JsonSkip
import ru.tinkoff.kora.json.common.annotation.JsonWriter
import tools.jackson.core.JsonGenerator

@JsonWriter
data class DtoOnlyWriter(
    val field1: String,
    @JsonField("renamedField2") val field2: String,
    @field:Mapping(FieldWriter::class) val field3: Inner,
    @JsonSkip val field4: String
) {
    class FieldWriter : ru.tinkoff.kora.json.common.JsonWriter<Inner?> {
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
