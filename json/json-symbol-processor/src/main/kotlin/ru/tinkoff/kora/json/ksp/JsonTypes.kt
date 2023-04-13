package ru.tinkoff.kora.json.ksp

import com.squareup.kotlinpoet.ClassName

object JsonTypes {

    val json = ClassName("ru.tinkoff.kora.json.common.annotation", "Json")
    val jsonDiscriminatorField = ClassName("ru.tinkoff.kora.json.common.annotation", "JsonDiscriminatorField")
    val jsonDiscriminatorValue = ClassName("ru.tinkoff.kora.json.common.annotation", "JsonDiscriminatorValue")


    val jsonReaderAnnotation = ClassName("ru.tinkoff.kora.json.common.annotation", "JsonReader")
    val jsonWriterAnnotation = ClassName("ru.tinkoff.kora.json.common.annotation", "JsonWriter")

    val jsonReader = ClassName("ru.tinkoff.kora.json.common", "JsonReader")
    val jsonWriter = ClassName("ru.tinkoff.kora.json.common", "JsonWriter")

    val enumJsonReader = ClassName("ru.tinkoff.kora.json.common", "EnumJsonReader")
    val enumJsonWriter = ClassName("ru.tinkoff.kora.json.common", "EnumJsonWriter")

    val jsonFieldAnnotation = ClassName("ru.tinkoff.kora.json.common.annotation", "JsonField")
    val jsonSkipAnnotation = ClassName("ru.tinkoff.kora.json.common.annotation", "JsonSkip")

    val bufferedParserWithDiscriminator = ClassName("ru.tinkoff.kora.json.common", "BufferedParserWithDiscriminator")

    val jsonParseException = ClassName("com.fasterxml.jackson.core", "JsonParseException")
    val jsonParser = ClassName("com.fasterxml.jackson.core", "JsonParser")
    val jsonGenerator = ClassName("com.fasterxml.jackson.core", "JsonGenerator")
    val jsonToken = ClassName("com.fasterxml.jackson.core", "JsonToken")
    val serializedString = ClassName("com.fasterxml.jackson.core.io", "SerializedString")

}
