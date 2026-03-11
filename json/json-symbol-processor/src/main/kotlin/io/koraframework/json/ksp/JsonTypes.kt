package io.koraframework.json.ksp

import com.squareup.kotlinpoet.ClassName

object JsonTypes {

    val json = ClassName("io.koraframework.json.common.annotation", "Json")
    val jsonInclude = ClassName("io.koraframework.json.common.annotation", "JsonInclude")
    val jsonDiscriminatorField = ClassName("io.koraframework.json.common.annotation", "JsonDiscriminatorField")
    val jsonDiscriminatorValue = ClassName("io.koraframework.json.common.annotation", "JsonDiscriminatorValue")

    val jsonNullable = ClassName("io.koraframework.json.common", "JsonNullable")
    val jsonReaderAnnotation = ClassName("io.koraframework.json.common.annotation", "JsonReader")
    val jsonWriterAnnotation = ClassName("io.koraframework.json.common.annotation", "JsonWriter")

    val jsonReader = ClassName("io.koraframework.json.common", "JsonReader")
    val jsonWriter = ClassName("io.koraframework.json.common", "JsonWriter")

    val enumJsonReader = ClassName("io.koraframework.json.common", "EnumJsonReader")
    val enumJsonWriter = ClassName("io.koraframework.json.common", "EnumJsonWriter")

    val jsonFieldAnnotation = ClassName("io.koraframework.json.common.annotation", "JsonField")
    val jsonSkipAnnotation = ClassName("io.koraframework.json.common.annotation", "JsonSkip")

    val bufferingJsonParser = ClassName("io.koraframework.json.common.util", "BufferingJsonParser");
    val discriminatorHelper = ClassName("io.koraframework.json.common.util", "DiscriminatorHelper");

    val jsonParseException = ClassName("tools.jackson.core.exc", "StreamReadException")
    val jsonParser = ClassName("tools.jackson.core", "JsonParser")
    val jsonGenerator = ClassName("tools.jackson.core", "JsonGenerator")
    val jsonToken = ClassName("tools.jackson.core", "JsonToken")
    val jsonParserSequence = ClassName("tools.jackson.core.util", "JsonParserSequence")
    val serializedString = ClassName("tools.jackson.core.io", "SerializedString")
}
