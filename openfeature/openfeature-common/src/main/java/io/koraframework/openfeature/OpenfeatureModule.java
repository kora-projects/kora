package io.koraframework.openfeature;

import dev.openfeature.sdk.Value;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.FactoryModule;
import io.koraframework.json.common.JsonReader;
import io.koraframework.json.common.JsonWriter;
import io.koraframework.openfeature.mapper.OpenfeatureValueJsonReader;
import io.koraframework.openfeature.mapper.OpenfeatureValueJsonWriter;
import io.koraframework.openfeature.mapper.OpenfeatureMapperModule;

public interface OpenfeatureModule extends OpenfeatureMapperModule {

    @FactoryModule
    default OpenfeatureFactoryModule openfeatureFactory() {
        return new OpenfeatureFactoryModule("openfeature");
    }

    @DefaultComponent
    default JsonReader<Value> openfeatureValueJsonReader() {
        return new OpenfeatureValueJsonReader();
    }

    @DefaultComponent
    default JsonWriter<Value> openfeatureValueJsonWriter() {
        return new OpenfeatureValueJsonWriter();
    }
}
