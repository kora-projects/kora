package ru.tinkoff.kora.json.annotation.processor.dto;

import ru.tinkoff.kora.json.common.annotation.Json;

@Json
public record DtoWithObject(Object value) {}
