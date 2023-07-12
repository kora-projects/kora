package ru.tinkoff.kora.json.annotation.processor;

import com.squareup.javapoet.ClassName;

public class JsonTypes {
    public static final ClassName json = ClassName.get("ru.tinkoff.kora.json.common.annotation", "Json");
    public static final ClassName jsonInclude = ClassName.get("ru.tinkoff.kora.json.common.annotation", "JsonInclude");
    public static final ClassName jsonDiscriminatorField = ClassName.get("ru.tinkoff.kora.json.common.annotation", "JsonDiscriminatorField");
    public static final ClassName jsonDiscriminatorValue = ClassName.get("ru.tinkoff.kora.json.common.annotation", "JsonDiscriminatorValue");


    public static final ClassName jsonReaderAnnotation = ClassName.get("ru.tinkoff.kora.json.common.annotation", "JsonReader");
    public static final ClassName jsonWriterAnnotation = ClassName.get("ru.tinkoff.kora.json.common.annotation", "JsonWriter");

    public static final ClassName jsonReader = ClassName.get("ru.tinkoff.kora.json.common", "JsonReader");
    public static final ClassName jsonWriter = ClassName.get("ru.tinkoff.kora.json.common", "JsonWriter");

    public static final ClassName enumJsonReader = ClassName.get("ru.tinkoff.kora.json.common", "EnumJsonReader");
    public static final ClassName enumJsonWriter = ClassName.get("ru.tinkoff.kora.json.common", "EnumJsonWriter");

    public static final ClassName jsonFieldAnnotation = ClassName.get("ru.tinkoff.kora.json.common.annotation", "JsonField");
    public static final ClassName jsonSkipAnnotation = ClassName.get("ru.tinkoff.kora.json.common.annotation", "JsonSkip");

    public static final ClassName bufferingJsonParser = ClassName.get("ru.tinkoff.kora.json.common.util", "BufferingJsonParser");
    public static final ClassName discriminatorHelper = ClassName.get("ru.tinkoff.kora.json.common.util", "DiscriminatorHelper");

    public static final ClassName jsonParseException = ClassName.get("com.fasterxml.jackson.core", "JsonParseException");
    public static final ClassName jsonParser = ClassName.get("com.fasterxml.jackson.core", "JsonParser");
    public static final ClassName jsonParserSequence = ClassName.get("com.fasterxml.jackson.core.util", "JsonParserSequence");
    public static final ClassName jsonGenerator = ClassName.get("com.fasterxml.jackson.core", "JsonGenerator");
    public static final ClassName jsonToken = ClassName.get("com.fasterxml.jackson.core", "JsonToken");
    public static final ClassName serializedString = ClassName.get("com.fasterxml.jackson.core.io", "SerializedString");

}
