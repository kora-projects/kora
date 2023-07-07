package ru.tinkoff.kora.json.common.util;

import com.fasterxml.jackson.core.JsonToken;

public record JsonSegment(JsonToken token, char[] data) {
}
