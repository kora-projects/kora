package io.koraframework.json.annotation.processor;

import com.palantir.javapoet.ClassName;

public class JsonTypes {
    public static final ClassName json = ClassName.get("io.koraframework.json.common.annotation", "Json");
    public static final ClassName jsonInclude = ClassName.get("io.koraframework.json.common.annotation", "JsonInclude");
    public static final ClassName jsonDiscriminatorField = ClassName.get("io.koraframework.json.common.annotation", "JsonDiscriminatorField");
    public static final ClassName jsonDiscriminatorValue = ClassName.get("io.koraframework.json.common.annotation", "JsonDiscriminatorValue");

    public static final ClassName jsonReaderAnnotation = ClassName.get("io.koraframework.json.common.annotation", "JsonReader");
    public static final ClassName jsonWriterAnnotation = ClassName.get("io.koraframework.json.common.annotation", "JsonWriter");

    public static final ClassName jsonNullable = ClassName.get("io.koraframework.json.common", "JsonNullable");
    public static final ClassName jsonReader = ClassName.get("io.koraframework.json.common", "JsonReader");
    public static final ClassName jsonWriter = ClassName.get("io.koraframework.json.common", "JsonWriter");

    public static final ClassName enumJsonReader = ClassName.get("io.koraframework.json.common", "EnumJsonReader");
    public static final ClassName enumJsonWriter = ClassName.get("io.koraframework.json.common", "EnumJsonWriter");

    public static final ClassName jsonFieldAnnotation = ClassName.get("io.koraframework.json.common.annotation", "JsonField");
    public static final ClassName jsonSkipAnnotation = ClassName.get("io.koraframework.json.common.annotation", "JsonSkip");

    public static final ClassName bufferingJsonParser = ClassName.get("io.koraframework.json.common.util", "BufferingJsonParser");
    public static final ClassName discriminatorHelper = ClassName.get("io.koraframework.json.common.util", "DiscriminatorHelper");

    public static final ClassName jsonParseException = ClassName.get("tools.jackson.core.exc", "StreamReadException");
    public static final ClassName jsonParser = ClassName.get("tools.jackson.core", "JsonParser");
    public static final ClassName jsonParserSequence = ClassName.get("tools.jackson.core.util", "JsonParserSequence");
    public static final ClassName jsonGenerator = ClassName.get("tools.jackson.core", "JsonGenerator");
    public static final ClassName jsonToken = ClassName.get("tools.jackson.core", "JsonToken");
    public static final ClassName serializedString = ClassName.get("tools.jackson.core.io", "SerializedString");

}
