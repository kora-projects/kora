package ru.tinkoff.kora.json.ksp.dto

import ru.tinkoff.kora.json.common.annotation.Json
import ru.tinkoff.kora.json.common.annotation.JsonReader

@Json
data class KotlinDataClassDtoWithNonPrimaryConstructor(val field1: String, val field2: String?) {
    @JsonReader
    constructor(field1: String) : this(field1, null)
}
