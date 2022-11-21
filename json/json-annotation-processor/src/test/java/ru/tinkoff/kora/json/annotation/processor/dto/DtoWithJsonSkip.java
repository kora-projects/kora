package ru.tinkoff.kora.json.annotation.processor.dto;

import ru.tinkoff.kora.json.common.annotation.Json;
import ru.tinkoff.kora.json.common.annotation.JsonSkip;

@Json
public record DtoWithJsonSkip(String field1, String field2, @JsonSkip String field3, @JsonSkip String field4) {

}
