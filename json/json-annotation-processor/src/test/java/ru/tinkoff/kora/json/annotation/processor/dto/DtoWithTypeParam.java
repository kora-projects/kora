package ru.tinkoff.kora.json.annotation.processor.dto;

import ru.tinkoff.kora.json.common.annotation.Json;
import ru.tinkoff.kora.json.common.annotation.JsonDiscriminatorField;

@Json
@JsonDiscriminatorField("@type")
public sealed interface DtoWithTypeParam<A, B> {
    record FirstTpe<A, B>(A a, B b, int c) implements DtoWithTypeParam<A, B> {}
    record SecondTpe<A>(A a) implements DtoWithTypeParam<A, Object> {}
    record ThirdTpe<B>(B b) implements DtoWithTypeParam<Object, B> {}
}
