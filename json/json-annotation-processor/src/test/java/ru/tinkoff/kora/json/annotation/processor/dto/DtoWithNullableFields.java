package ru.tinkoff.kora.json.annotation.processor.dto;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.json.common.annotation.JsonField;
import ru.tinkoff.kora.json.common.annotation.JsonReader;

@JsonReader
public record DtoWithNullableFields(@JsonField("field_1") String field1, int field4, @Nullable String field2, @Nullable String field3) {}
