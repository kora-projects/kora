package ru.tinkoff.kora.json.annotation.processor.dto;

import ru.tinkoff.kora.json.common.annotation.Json;
import ru.tinkoff.kora.json.common.annotation.JsonDiscriminatorField;
import ru.tinkoff.kora.json.common.annotation.JsonDiscriminatorValue;

@Json
@JsonDiscriminatorField("type")
public sealed interface AnnotatedSealedDto {
    @JsonDiscriminatorValue("first_dto")
    record FirstDto(String value) implements AnnotatedSealedDto {}

    record SecondDto(String val, int dig) implements AnnotatedSealedDto {}
}
