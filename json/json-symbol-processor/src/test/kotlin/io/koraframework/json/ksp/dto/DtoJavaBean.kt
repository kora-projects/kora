package io.koraframework.json.ksp.dto

import io.koraframework.json.common.annotation.JsonField
import io.koraframework.json.common.annotation.JsonWriter

@JsonWriter
class DtoJavaBean(@JsonField("string_field") var field1: String?, @JsonField("int_field") var field2: Int)
