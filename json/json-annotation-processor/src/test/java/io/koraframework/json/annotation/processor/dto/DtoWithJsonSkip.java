package io.koraframework.json.annotation.processor.dto;

import io.koraframework.json.common.annotation.Json;
import io.koraframework.json.common.annotation.JsonSkip;

@Json
public record DtoWithJsonSkip(String field1, String field2, @JsonSkip String field3, @JsonSkip String field4) {

}
