package io.koraframework.json.annotation.processor.dto;

import io.koraframework.json.common.annotation.Json;

@Json
public record DtoWithObject(Object value) {}
