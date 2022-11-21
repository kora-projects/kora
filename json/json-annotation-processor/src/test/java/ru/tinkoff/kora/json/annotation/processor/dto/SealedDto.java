package ru.tinkoff.kora.json.annotation.processor.dto;


import ru.tinkoff.kora.json.common.annotation.Json;
import ru.tinkoff.kora.json.common.annotation.JsonDiscriminatorField;

@Json
@JsonDiscriminatorField("@type")
public sealed interface SealedDto {
    record FirstDto(InnerDto someInnerObject, String firstValue) implements SealedDto {
        @Json
        public record InnerDto(String v){}
    }
    record SecondDto(String firstValue, int secondValue) implements SealedDto {}
    record ThirdDto(String firstValue, int secondValue, boolean thirdValue) implements SealedDto {}
}



