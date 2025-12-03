package ru.tinkoff.kora.json.common.util;

import tools.jackson.core.JsonToken;

public record JsonSegment(JsonToken token, char[] data, boolean isNumberNegative) {
}
