package ru.tinkoff.kora.json.annotation.processor.dto;

import ru.tinkoff.kora.common.NamingStrategy;
import ru.tinkoff.kora.common.naming.SnakeCaseNameConverter;
import ru.tinkoff.kora.json.common.annotation.Json;

@Json
@NamingStrategy(SnakeCaseNameConverter.class)
public record DtoWithSnakeCaseNaming(String stringField, Integer integerField) {
}
