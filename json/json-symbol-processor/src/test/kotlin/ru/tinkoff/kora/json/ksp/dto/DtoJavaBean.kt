package ru.tinkoff.kora.json.ksp.dto

import ru.tinkoff.kora.json.common.annotation.JsonField
import ru.tinkoff.kora.json.common.annotation.JsonWriter

@JsonWriter
class DtoJavaBean(@JsonField("string_field") var field1: String?, @JsonField("int_field") var field2: Int)
