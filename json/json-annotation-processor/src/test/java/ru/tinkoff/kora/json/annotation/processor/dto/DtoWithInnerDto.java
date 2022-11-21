package ru.tinkoff.kora.json.annotation.processor.dto;

import ru.tinkoff.kora.json.common.annotation.Json;

import java.util.List;
import java.util.Map;

@Json
public record DtoWithInnerDto(
    InnerDto inner,
    List<InnerDto> field2,
    Map<String, InnerDto> field3,
    List<List<InnerDto>> field4) {

    @Json
    public record InnerDto(String field1) {

    }
}
