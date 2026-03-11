package io.koraframework.json.ksp.dto

import io.koraframework.json.common.annotation.Json
import io.koraframework.json.common.annotation.JsonReader

@Json
data class KotlinDataClassDtoWithNonPrimaryConstructor(val field1: String, val field2: String?) {
    @JsonReader
    constructor(field1: String) : this(field1, null)
}
