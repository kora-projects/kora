package ru.tinkoff.kora.json.common;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

class JsonReaderTest {
    sealed interface MValue {
        record MDoc(LinkedHashMap<String, MValue> store) implements MValue {}

        record MArray(List<MValue> values) implements MValue {}

        record MDouble(double value) implements MValue {}
        record MLong(long value) implements MValue {}
        record MString(String value) implements MValue {}
        record MBoolean(boolean value) implements MValue {}
        record MNull() implements MValue {}
    }

    @Test
    void name() {
        var json = """
            """;

    }
}
