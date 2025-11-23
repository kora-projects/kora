package ru.tinkoff.kora.json.ksp

import com.squareup.kotlinpoet.ClassName

object JsonTypes {

    val json = ClassName("ru.tinkoff.kora.json.common.annotation", "Json")
    val jsonInclude = ClassName("ru.tinkoff.kora.json.common.annotation", "JsonInclude")
    val jsonDiscriminatorField = ClassName("ru.tinkoff.kora.json.common.annotation", "JsonDiscriminatorField")
    val jsonDiscriminatorValue = ClassName("ru.tinkoff.kora.json.common.annotation", "JsonDiscriminatorValue")

    val jsonNullable = ClassName("ru.tinkoff.kora.json.common", "JsonNullable")
    val jsonReaderAnnotation = ClassName("ru.tinkoff.kora.json.common.annotation", "JsonReader")
    val jsonWriterAnnotation = ClassName("ru.tinkoff.kora.json.common.annotation", "JsonWriter")

    val jsonReader = ClassName("ru.tinkoff.kora.json.common", "JsonReader")
    val jsonWriter = ClassName("ru.tinkoff.kora.json.common", "JsonWriter")

    val enumJsonReader = ClassName("ru.tinkoff.kora.json.common", "EnumJsonReader")
    val enumJsonWriter = ClassName("ru.tinkoff.kora.json.common", "EnumJsonWriter")

    val jsonFieldAnnotation = ClassName("ru.tinkoff.kora.json.common.annotation", "JsonField")
    val jsonSkipAnnotation = ClassName("ru.tinkoff.kora.json.common.annotation", "JsonSkip")

    val bufferingJsonParser = ClassName("ru.tinkoff.kora.json.common.util", "BufferingJsonParser");
    val discriminatorHelper = ClassName("ru.tinkoff.kora.json.common.util", "DiscriminatorHelper");

    val jsonParseException = ClassName("tools.jackson.core.exc", "StreamReadException")
    val jsonParser = ClassName("tools.jackson.core", "JsonParser")
    val jsonGenerator = ClassName("tools.jackson.core", "JsonGenerator")
    val jsonToken = ClassName("tools.jackson.core", "JsonToken")
    val jsonParserSequence = ClassName("tools.jackson.core.util", "JsonParserSequence")
    val serializedString = ClassName("tools.jackson.core.io", "SerializedString")
}
