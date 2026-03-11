package io.koraframework.json.annotation.processor.dto;

import org.jspecify.annotations.Nullable;
import io.koraframework.json.common.annotation.JsonField;
import io.koraframework.json.common.annotation.JsonReader;

@JsonReader
public record DtoWithNullableFields(@JsonField("field_1") String field1, int field4, @Nullable String field2, @Nullable String field3) {}
