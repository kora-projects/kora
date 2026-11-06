package io.koraframework.json.annotation.processor.dto;

import io.koraframework.json.common.annotation.Json;
import io.koraframework.json.common.annotation.JsonDiscriminatorField;

@Json
@JsonDiscriminatorField("@type")
public sealed interface DtoWithRenamedTypeParam<T> {
    @Json
    record Ok<V>(V data) implements DtoWithRenamedTypeParam<V> {}

    @Json
    record Fail<X>(String error) implements DtoWithRenamedTypeParam<X> {}

    @Json
    record Text<E>(String text) implements DtoWithRenamedTypeParam<String> {}
}
