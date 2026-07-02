package ru.tinkoff.kora.json.annotation.processor;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.json.common.JsonReader;
import ru.tinkoff.kora.json.common.JsonWriter;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DelegatingValueTest extends AbstractJsonAnnotationProcessorTest {
    JsonReader<String> stringReader = JsonParser::getValueAsString;
    JsonWriter<String> stringWriter = JsonGenerator::writeString;
    JsonReader<Long> longReader = JsonParser::getLongValue;
    JsonWriter<Long> longWriter = JsonGenerator::writeNumber;

    private Object newObject(String name, Class<?> argType, Object arg) throws Exception {
        return compileResult.loadClass(name).getDeclaredConstructor(argType).newInstance(arg);
    }

    private void assertWrite(JsonWriter<Object> w, Object value, String expectedJson) throws IOException {
        assertThat(w.toByteArray(value)).asString(StandardCharsets.UTF_8).isEqualTo(expectedJson);
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
    public void testDelegatingWriterWinsOverJsonProperties() throws Exception {
        compile("""
            @Json
            public record Money(long amount, String currency) {
              @JsonWriter public long amount() { return amount; }
            }
            """);
        compileResult.assertSuccess();
        var w = writer("Money", longWriter);
        var money = compileResult.loadClass("Money").getDeclaredConstructor(long.class, String.class).newInstance(100L, "USD");
        assertWrite(w, money, "100");
    }

    @Test
    public void testDelegatingReaderFromExtension() {
        compile(List.of(new KoraAppProcessor(), new JsonAnnotationProcessor()), """
            @ru.tinkoff.kora.common.KoraApp
            public interface TestApp {
              record UserId(long id) {
                @JsonReader public static UserId of(long v) { return new UserId(v); }
              }

              default ru.tinkoff.kora.json.common.JsonReader<Long> longReader() { return com.fasterxml.jackson.core.JsonParser::getLongValue; }
              default ru.tinkoff.kora.json.common.JsonWriter<Long> longWriter() { return com.fasterxml.jackson.core.JsonGenerator::writeNumber; }

              @Root
              default String root(ru.tinkoff.kora.json.common.JsonReader<UserId> r) { return ""; }
            }
            """);
        compileResult.assertSuccess();
        assertThat(reader("TestApp_UserId", longReader)).isNotNull();
    }

    @Test
    public void testDelegatingWriterFromExtension() {
        compile(List.of(new KoraAppProcessor(), new JsonAnnotationProcessor()), """
            @ru.tinkoff.kora.common.KoraApp
            public interface TestApp {
              record UserId(long id) {
                @JsonWriter public long id() { return id; }
              }

              default ru.tinkoff.kora.json.common.JsonReader<Long> longReader() { return com.fasterxml.jackson.core.JsonParser::getLongValue; }
              default ru.tinkoff.kora.json.common.JsonWriter<Long> longWriter() { return com.fasterxml.jackson.core.JsonGenerator::writeNumber; }

              @Root
              default String root(ru.tinkoff.kora.json.common.JsonWriter<UserId> r) { return ""; }
            }
            """);
        compileResult.assertSuccess();
        assertThat(writer("TestApp_UserId", longWriter)).isNotNull();
    }

    @Test
    public void testMultipleWriterMethodsFails() {
        var result = compile(List.of(new JsonAnnotationProcessor()), """
            public record UserId(long id) {
              @JsonWriter public long a() { return id; }
              @JsonWriter public long b() { return id; }
            }
            """);
        assertThat(result.isFailed()).isTrue();
        assertThat(result.errors()).anyMatch(d -> d.getMessage(null).contains("multiple @JsonWriter methods"));
    }

    @Test
    public void testWriterInstanceMethodWithParamsFails() {
        var result = compile(List.of(new JsonAnnotationProcessor()), """
            public class UserId {
              private final long id;
              public UserId(long id) { this.id = id; }
              @JsonWriter public long toJson(int x) { return id; }
            }
            """);
        assertThat(result.isFailed()).isTrue();
        assertThat(result.errors()).anyMatch(d -> d.getMessage(null).contains("must have no parameters"));
    }

    @Test
    public void testReaderFactoryNotStaticFails() {
        var result = compile(List.of(new JsonAnnotationProcessor()), """
            public class UserId {
              private final long id;
              public UserId(long id) { this.id = id; }
              @JsonReader public UserId of(long v) { return new UserId(v); }
            }
            """);
        assertThat(result.isFailed()).isTrue();
        assertThat(result.errors()).anyMatch(d -> d.getMessage(null).contains("must be public static"));
    }

    @Test
    public void testReaderFactoryWrongReturnFails() {
        var result = compile(List.of(new JsonAnnotationProcessor()), """
            public class UserId {
              private final long id;
              public UserId(long id) { this.id = id; }
              @JsonReader public static String of(long v) { return String.valueOf(v); }
            }
            """);
        assertThat(result.isFailed()).isTrue();
        assertThat(result.errors()).anyMatch(d -> d.getMessage(null).contains("must return"));
    }

    @Test
    public void testFactoryAndReaderConstructorConflictFails() {
        var result = compile(List.of(new JsonAnnotationProcessor()), """
            public class UserId {
              private final long id;
              @JsonReader public UserId(long id) { this.id = id; }
              @JsonReader public static UserId of(long v) { return new UserId(v); }
            }
            """);
        assertThat(result.isFailed()).isTrue();
        assertThat(result.errors()).anyMatch(d -> d.getMessage(null).contains("only one is allowed"));
    }

    @Test
    public void testWriterOnUnsupportedEnclosingFails() {
        var result = compile(List.of(new JsonAnnotationProcessor()), """
            public interface Holder {
              @JsonWriter default long toJson() { return 1L; }
            }
            """);
        assertThat(result.isFailed()).isTrue();
        assertThat(result.errors()).anyMatch(d -> d.getMessage(null).contains("supported only for a method of a class or enum"));
    }
}
