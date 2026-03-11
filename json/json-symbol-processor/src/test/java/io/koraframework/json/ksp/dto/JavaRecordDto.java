package io.koraframework.json.ksp.dto;

import io.koraframework.json.common.annotation.Json;
import io.koraframework.json.common.annotation.JsonField;
import io.koraframework.json.common.annotation.JsonSkip;

@Json
public record JavaRecordDto (
    @JsonField("field1")
    String string,
    Integer integer,
    @JsonSkip
    Boolean bool
) {

}
