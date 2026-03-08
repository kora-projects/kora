package io.koraframework.json.annotation.processor.dto;

import io.koraframework.common.NamingStrategy;
import io.koraframework.common.naming.SnakeCaseNameConverter;
import io.koraframework.json.common.annotation.Json;

@Json
@NamingStrategy(SnakeCaseNameConverter.class)
public record DtoWithSnakeCaseNaming(String stringField, Integer integerField) {
}
