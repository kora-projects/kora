package io.koraframework.json.common;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tools.jackson.core.exc.StreamReadException;

class JsonNumberTests extends Assertions implements JsonModule {

    @Test
    void doubleNonFiniteRoundTrip() {
        for (var value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertEquals(value, doubleJsonReader().read(doubleJsonWriter().toByteArray(value)));
        }
        assertThrows(StreamReadException.class, () -> doubleJsonReader().read("\"42\""));
    }

    @Test
    void floatNonFiniteRoundTrip() {
        for (var value : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            assertEquals(value, floatJsonReader().read(floatJsonWriter().toByteArray(value)));
        }
        assertThrows(StreamReadException.class, () -> floatJsonReader().read("\"42\""));
    }
}
