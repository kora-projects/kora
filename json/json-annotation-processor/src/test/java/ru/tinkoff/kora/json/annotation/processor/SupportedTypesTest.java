package ru.tinkoff.kora.json.annotation.processor;

import com.fasterxml.jackson.core.JsonParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SupportedTypesTest extends AbstractJsonAnnotationProcessorTest {
    @Test
    public void testInt() throws IOException {
        compile("""
            @Json
            public record TestRecord(int value) {
            }
            """);

        compileResult.assertSuccess();

        var mapper = mapper("TestRecord");
        mapper.verify(newObject("TestRecord", 42), "{\"value\":42}");
        assertThatThrownBy(() -> mapper.read("{\"value\":null}")).isInstanceOf(JsonParseException.class);
    }

    @Test
    public void testInteger() throws IOException {
        compile("""
            @Json
            public record TestRecord(Integer value) {
            }
            """);

        compileResult.assertSuccess();

        var mapper = mapper("TestRecord");
        mapper.verify(newObject("TestRecord", 42), "{\"value\":42}");
        assertThatThrownBy(() -> mapper.read("{\"value\":null}")).isInstanceOf(JsonParseException.class);
    }

    @Test
    public void testNullableInteger() throws IOException {
        compile("""
            @Json
            public record TestRecord(@Nullable Integer value) {
            }
            """);

        compileResult.assertSuccess();

        var mapper = mapper("TestRecord");
        mapper.verify(newObject("TestRecord", 42), "{\"value\":42}");
        mapper.verify(newObject("TestRecord", new Object[]{null}), "{}");
        mapper.verifyRead("{\"value\":null}", newObject("TestRecord", new Object[]{null}));
    }

    @Test
    public void testLong() throws IOException {
        compile("""
            @Json
            public record TestRecord(long value) {
            }
            """);

        compileResult.assertSuccess();

        var mapper = mapper("TestRecord");
        mapper.verify(newObject("TestRecord", 42L), "{\"value\":42}");
        assertThatThrownBy(() -> mapper.read("{\"value\":null}")).isInstanceOf(JsonParseException.class);
    }

    @Test
    public void testLongObject() throws IOException {
        compile("""
            @Json
            public record TestRecord(Long value) {
            }
            """);

        compileResult.assertSuccess();

        var mapper = mapper("TestRecord");
        mapper.verify(newObject("TestRecord", 42L), "{\"value\":42}");
        assertThatThrownBy(() -> mapper.read("{\"value\":null}")).isInstanceOf(JsonParseException.class);
    }

    @Test
    public void testNullableLong() throws IOException {
        compile("""
            @Json
            public record TestRecord(@Nullable Long value) {
            }
            """);

        compileResult.assertSuccess();

        var mapper = mapper("TestRecord");
        mapper.verify(newObject("TestRecord", 42L), "{\"value\":42}");
        mapper.verify(newObject("TestRecord", new Object[]{null}), "{}");
        mapper.verifyRead("{\"value\":null}", newObject("TestRecord", new Object[]{null}));
    }

    @Test
    public void testShort() throws IOException {
        compile("""
            @Json
            public record TestRecord(short value) {
            }
            """);

        compileResult.assertSuccess();

        var mapper = mapper("TestRecord");
        mapper.verify(newObject("TestRecord", (short) 42), "{\"value\":42}");
        assertThatThrownBy(() -> mapper.read("{\"value\":null}")).isInstanceOf(JsonParseException.class);
    }

    @Test
    public void testShortObject() throws IOException {
        compile("""
            @Json
            public record TestRecord(Short value) {
            }
            """);

        compileResult.assertSuccess();

        var mapper = mapper("TestRecord");
        mapper.verify(newObject("TestRecord", (short) 42), "{\"value\":42}");
        assertThatThrownBy(() -> mapper.read("{\"value\":null}")).isInstanceOf(JsonParseException.class);
    }

    @Test
    public void testNullableShort() throws IOException {
        compile("""
            @Json
            public record TestRecord(@Nullable Short value) {
            }
            """);

        compileResult.assertSuccess();

        var mapper = mapper("TestRecord");
        mapper.verify(newObject("TestRecord", (short) 42), "{\"value\":42}");
        mapper.verify(newObject("TestRecord", new Object[]{null}), "{}");
        mapper.verifyRead("{\"value\":null}", newObject("TestRecord", new Object[]{null}));
    }

    @Test
    public void testFloat() throws IOException {
        compile("""
            @Json
            public record TestRecord(float value) {
            }
            """);

        compileResult.assertSuccess();

        var mapper = mapper("TestRecord");
        mapper.verify(newObject("TestRecord", 42.1f), "{\"value\":42.1}");
        mapper.verifyRead("{\"value\":42}", newObject("TestRecord", 42f));
        assertThatThrownBy(() -> mapper.read("{\"value\":null}")).isInstanceOf(JsonParseException.class);
    }

    @Test
    public void testFloatObject() throws IOException {
        compile("""
            @Json
            public record TestRecord(Float value) {
            }
            """);

        compileResult.assertSuccess();

        var mapper = mapper("TestRecord");
        mapper.verify(newObject("TestRecord", 42.1f), "{\"value\":42.1}");
        mapper.verifyRead("{\"value\":42}", newObject("TestRecord", 42f));
        assertThatThrownBy(() -> mapper.read("{\"value\":null}")).isInstanceOf(JsonParseException.class);
    }

    @Test
    public void testNullableFloat() throws IOException {
        compile("""
            @Json
            public record TestRecord(@Nullable Float value) {
            }
            """);

        compileResult.assertSuccess();

        var mapper = mapper("TestRecord");
        mapper.verify(newObject("TestRecord", 42.1f), "{\"value\":42.1}");
        mapper.verifyRead("{\"value\":42}", newObject("TestRecord", 42f));
        mapper.verify(newObject("TestRecord", new Object[]{null}), "{}");
        mapper.verifyRead("{\"value\":null}", newObject("TestRecord", new Object[]{null}));
    }

    @Test
    public void testDouble() throws IOException {
        compile("""
            @Json
            public record TestRecord(double value) {
            }
            """);

        compileResult.assertSuccess();

        var mapper = mapper("TestRecord");
        mapper.verify(newObject("TestRecord", 42.1d), "{\"value\":42.1}");
        mapper.verifyRead("{\"value\":42}", newObject("TestRecord", 42d));
        assertThatThrownBy(() -> mapper.read("{\"value\":null}")).isInstanceOf(JsonParseException.class);
    }

    @Test
    public void testDoubleObject() throws IOException {
        compile("""
            @Json
            public record TestRecord(Double value) {
            }
            """);

        compileResult.assertSuccess();

        var mapper = mapper("TestRecord");
        mapper.verify(newObject("TestRecord", 42.1d), "{\"value\":42.1}");
        mapper.verifyRead("{\"value\":42}", newObject("TestRecord", 42d));
        assertThatThrownBy(() -> mapper.read("{\"value\":null}")).isInstanceOf(JsonParseException.class);
    }

    @Test
    public void testNullableDouble() throws IOException {
        compile("""
            @Json
            public record TestRecord(@Nullable Double value) {
            }
            """);

        compileResult.assertSuccess();

        var mapper = mapper("TestRecord");
        mapper.verify(newObject("TestRecord", 42.1d), "{\"value\":42.1}");
        mapper.verifyRead("{\"value\":42}", newObject("TestRecord", 42d));
        mapper.verify(newObject("TestRecord", new Object[]{null}), "{}");
        mapper.verifyRead("{\"value\":null}", newObject("TestRecord", new Object[]{null}));
    }

    @Test
    public void testBoolean() throws IOException {
        compile("""
            @Json
            public record TestRecord(boolean value) {
            }
            """);

        compileResult.assertSuccess();

        var mapper = mapper("TestRecord");
        mapper.verify(newObject("TestRecord", true), "{\"value\":true}");
        mapper.verify(newObject("TestRecord", false), "{\"value\":false}");
        assertThatThrownBy(() -> mapper.read("{\"value\":null}")).isInstanceOf(JsonParseException.class);
    }

    @Test
    public void testBooleanObject() throws IOException {
        compile("""
            @Json
            public record TestRecord(Boolean value) {
            }
            """);

        compileResult.assertSuccess();

        var mapper = mapper("TestRecord");
        mapper.verify(newObject("TestRecord", true), "{\"value\":true}");
        mapper.verify(newObject("TestRecord", false), "{\"value\":false}");
        assertThatThrownBy(() -> mapper.read("{\"value\":null}")).isInstanceOf(JsonParseException.class);
    }

    @Test
    public void testNullableBoolean() throws IOException {
        compile("""
            @Json
            public record TestRecord(@Nullable Boolean value) {
            }
            """);

        compileResult.assertSuccess();

        var mapper = mapper("TestRecord");
        mapper.verify(newObject("TestRecord", true), "{\"value\":true}");
        mapper.verify(newObject("TestRecord", false), "{\"value\":false}");
        mapper.verify(newObject("TestRecord", new Object[]{null}), "{}");
        mapper.verifyRead("{\"value\":null}", newObject("TestRecord", new Object[]{null}));
    }

    @Test
    public void testString() throws IOException {
        compile("""
            @Json
            public record TestRecord(String value) {
            }
            """);

        compileResult.assertSuccess();

        var mapper = mapper("TestRecord");
        mapper.verify(newObject("TestRecord", "test"), "{\"value\":\"test\"}");
        assertThatThrownBy(() -> mapper.read("{\"value\":null}")).isInstanceOf(JsonParseException.class);
    }

    @Test
    public void testNullableString() throws IOException {
        compile("""
            @Json
            public record TestRecord(@Nullable String value) {
            }
            """);

        compileResult.assertSuccess();

        var mapper = mapper("TestRecord");
        mapper.verify(newObject("TestRecord", "test"), "{\"value\":\"test\"}");
        mapper.verify(newObject("TestRecord", new Object[]{null}), "{}");
        mapper.verifyRead("{\"value\":null}", newObject("TestRecord", new Object[]{null}));
    }

    @Test
    public void testUuid() throws IOException {
        compile("""
            @Json
            public record TestRecord(java.util.UUID value) {
            }
            """);

        compileResult.assertSuccess();
        var uuid = java.util.UUID.randomUUID();

        var mapper = mapper("TestRecord");
        mapper.verify(newObject("TestRecord", uuid), "{\"value\":\"" + uuid + "\"}");
        assertThatThrownBy(() -> mapper.read("{\"value\":null}")).isInstanceOf(JsonParseException.class);
    }

    @Test
    public void testNullableUuid() throws IOException {
        compile("""
            @Json
            public record TestRecord(@Nullable java.util.UUID value) {
            }
            """);

        compileResult.assertSuccess();
        var uuid = java.util.UUID.randomUUID();

        var mapper = mapper("TestRecord");
        mapper.verify(newObject("TestRecord", uuid), "{\"value\":\"" + uuid + "\"}");
        mapper.verify(newObject("TestRecord", new Object[]{null}), "{}");
        mapper.verifyRead("{\"value\":null}", newObject("TestRecord", new Object[]{null}));
    }

    @Test
    public void testBigInteger() throws IOException {
        compile("""
            @Json
            public record TestRecord(java.math.BigInteger value) {
            }
            """);

        compileResult.assertSuccess();

        var mapper = mapper("TestRecord");
        mapper.verify(newObject("TestRecord", new BigInteger("42")), "{\"value\":42}");
        assertThatThrownBy(() -> mapper.read("{\"value\":null}")).isInstanceOf(JsonParseException.class);
    }

    @Test
    public void testNullableBigInteger() throws IOException {
        compile("""
            @Json
            public record TestRecord(@Nullable java.math.BigInteger value) {
            }
            """);

        compileResult.assertSuccess();

        var mapper = mapper("TestRecord");
        mapper.verify(newObject("TestRecord", new BigInteger("42")), "{\"value\":42}");
        mapper.verify(newObject("TestRecord", new Object[]{null}), "{}");
        mapper.verifyRead("{\"value\":null}", newObject("TestRecord", new Object[]{null}));
    }

    @Test
    public void testBigDecimal() throws IOException {
        compile("""
            @Json
            public record TestRecord(java.math.BigDecimal value) {
            }
            """);

        compileResult.assertSuccess();

        var mapper = mapper("TestRecord");
        mapper.verify(newObject("TestRecord", new BigDecimal("42")), "{\"value\":42}");
        mapper.verify(newObject("TestRecord", new BigDecimal("42.43")), "{\"value\":42.43}");
        assertThatThrownBy(() -> mapper.read("{\"value\":null}")).isInstanceOf(JsonParseException.class);
    }

    @Test
    public void testNullableBigDecimal() throws IOException {
        compile("""
            @Json
            public record TestRecord(@Nullable java.math.BigDecimal value) {
            }
            """);

        compileResult.assertSuccess();

        var mapper = mapper("TestRecord");
        mapper.verify(newObject("TestRecord", new BigDecimal("42")), "{\"value\":42}");
        mapper.verify(newObject("TestRecord", new BigDecimal("42.43")), "{\"value\":42.43}");
        mapper.verify(newObject("TestRecord", new Object[]{null}), "{}");
        mapper.verifyRead("{\"value\":null}", newObject("TestRecord", new Object[]{null}));
    }

    @Test
    public void testBinary() throws IOException {
        compile("""
            @Json
            public record TestRecord(byte[] value) {
              @Override
              public boolean equals(Object o) {
                return o instanceof TestRecord that && java.util.Arrays.equals(this.value, that.value);
              }
            }
            """);

        compileResult.assertSuccess();
        var b = new byte[]{1, 2, 3, 4};

        var mapper = mapper("TestRecord");
        mapper.verify(newObject("TestRecord", new Object[]{b}), "{\"value\":\"AQIDBA==\"}");
        assertThatThrownBy(() -> mapper.read("{\"value\":null}")).isInstanceOf(JsonParseException.class);
    }

    @Test
    public void testNullableBinary() throws IOException {
        compile("""
            @Json
            public record TestRecord(@Nullable byte[] value) {
              @Override
              public boolean equals(Object o) {
                return o instanceof TestRecord that && java.util.Arrays.equals(this.value, that.value);
              }
            }
            """);

        compileResult.assertSuccess();
        var b = new byte[]{1, 2, 3, 4};

        var mapper = mapper("TestRecord");
        mapper.verify(newObject("TestRecord", new Object[]{b}), "{\"value\":\"AQIDBA==\"}");
        mapper.verify(newObject("TestRecord", new Object[]{null}), "{}");
        mapper.verifyRead("{\"value\":null}", newObject("TestRecord", new Object[]{null}));
    }
}
