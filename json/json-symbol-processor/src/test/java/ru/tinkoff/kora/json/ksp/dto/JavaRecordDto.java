package ru.tinkoff.kora.json.ksp.dto;

import ru.tinkoff.kora.json.common.annotation.Json;
import ru.tinkoff.kora.json.common.annotation.JsonField;
import ru.tinkoff.kora.json.common.annotation.JsonSkip;

@Json
public record JavaRecordDto (
    @JsonField("field1")
    String string,
    Integer integer,
    @JsonSkip
    Boolean bool
) {

}
