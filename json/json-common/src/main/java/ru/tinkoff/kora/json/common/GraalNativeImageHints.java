package ru.tinkoff.kora.json.common;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.TSFBuilder;
import com.fasterxml.jackson.core.io.CharTypes;
import com.fasterxml.jackson.core.io.JsonStringEncoder;
import com.fasterxml.jackson.core.io.SerializedString;
import io.goodforgod.graalvm.hint.annotation.InitializationHint;

@InitializationHint(types = {
    JsonStringEncoder.class,
    CharTypes.class,
    TSFBuilder.class,
    SerializedString.class,
    JsonFactoryBuilder.class,
    JsonFactory.class,
    JsonCommonModule.class,
})
final class GraalNativeImageHints {

    private GraalNativeImageHints() { }
}
