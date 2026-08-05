package io.koraframework.json.annotation.processor;

import org.junit.jupiter.api.Test;
import io.koraframework.json.common.JsonReader;
import io.koraframework.json.common.JsonWriter;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DelegatingValueTest extends AbstractJsonAnnotationProcessorTest {
    JsonReader<Long> longReader = JsonParser::getLongValue;
    JsonWriter<Long> longWriter = JsonGenerator::writeNumber;
    JsonReader<String> stringReader = JsonParser::getValueAsString;
    JsonWriter<String> stringWriter = JsonGenerator::writeString;

    private Object newObject(String name, Class<?> argType, Object arg) throws Exception {
        return compileResult.loadClass(name).getDeclaredConstructor(argType).newInstance(arg);
    }

    @Test
    public void testDelegatingInstanceWriterAndFactoryReader() throws Exception {
        compile("""
            public record UserId(long id) {
              @JsonReader public static UserId of(long v) { return new UserId(v); }
              @JsonWriter public long id() { return id; }
            }
            """);
        compileResult.assertSuccess();
        var mapper = mapper("UserId", List.of(longReader), List.of(longWriter));
        mapper.verify(newObject("UserId", long.class, 42L), "42");
    }

    @Test
    public void testDelegatingStaticWriter() throws Exception {
        compile("""
            public record UserId(long id) {
              @JsonReader public static UserId of(long v) { return new UserId(v); }
              @JsonWriter public static long toJson(UserId u) { return u.id(); }
            }
            """);
        compileResult.assertSuccess();
        var mapper = mapper("UserId", List.of(longReader), List.of(longWriter));
        mapper.verify(newObject("UserId", long.class, 7L), "7");
    }

    @Test
    public void testDelegatingStringValue() throws Exception {
        compile("""
            public record Sku(String code) {
              @JsonReader public static Sku parse(String v) { return new Sku(v); }
              @JsonWriter public String code() { return code; }
            }
            """);
        compileResult.assertSuccess();
        var mapper = mapper("Sku", List.of(stringReader), List.of(stringWriter));
        mapper.verify(newObject("Sku", String.class, "ABC"), "\"ABC\"");
    }

    @Test
    public void testDelegatingWriterNullHandling() throws Exception {
        compile("""
            public record Sku(String code) {
              @JsonReader public static Sku parse(String v) { return new Sku(v); }
              @JsonWriter public String code() { return code; }
            }
            """);
        compileResult.assertSuccess();
        var w = writer("Sku", stringWriter);
        assertThat(w.toByteArray(null)).asString(StandardCharsets.UTF_8).isEqualTo("null");
    }
}
