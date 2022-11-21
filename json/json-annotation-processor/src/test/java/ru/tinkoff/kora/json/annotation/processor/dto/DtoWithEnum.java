package ru.tinkoff.kora.json.annotation.processor.dto;

import ru.tinkoff.kora.json.common.annotation.Json;

@Json
public record DtoWithEnum(TestEnum testEnum) {
    @Json
    public enum TestEnum {
        VAL1, VAL2
    }
}
