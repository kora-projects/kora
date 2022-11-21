package ru.tinkoff.kora.json.annotation.processor.dto;

import ru.tinkoff.kora.json.common.annotation.JsonField;
import ru.tinkoff.kora.json.common.annotation.JsonReader;

import javax.annotation.Nullable;

@JsonReader
public record DtoWithNullableFields(@JsonField("field_1") String field1, int field4, @Nullable String field2, @Nullable String field3) {}
