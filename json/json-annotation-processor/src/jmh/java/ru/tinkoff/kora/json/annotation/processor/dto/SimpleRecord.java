package ru.tinkoff.kora.json.annotation.processor.dto;

import ru.tinkoff.kora.json.common.annotation.Json;

@Json
public record SimpleRecord(int field1, String field2, boolean field3) {}
