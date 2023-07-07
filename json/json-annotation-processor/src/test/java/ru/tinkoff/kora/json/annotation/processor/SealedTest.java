package ru.tinkoff.kora.json.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;
import ru.tinkoff.kora.json.common.JsonReader;
import ru.tinkoff.kora.json.common.JsonWriter;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

public class SealedTest extends AbstractJsonAnnotationProcessorTest {
    @Test
    public void testSealedInterface() throws IOException {
        compile("""
            @Json
            @JsonDiscriminatorField("@type")
            public sealed interface TestInterface {
                @Json
                record Impl1(String value) implements TestInterface{}
                @Json
                record Impl2(int value) implements TestInterface{}
            }
            """);
        var o1 = newObject("TestInterface$Impl1", "test");
        var json1 = "{\"@type\":\"Impl1\",\"value\":\"test\"}";
        var o2 = newObject("TestInterface$Impl2", 42);
        var json2 = "{\"@type\":\"Impl2\",\"value\":42}";

        var m1 = mapper("TestInterface_Impl1");
        var m2 = mapper("TestInterface_Impl2");
        var m = mapper("TestInterface", List.of(m1, m2), List.of(m1, m2));

        assertThat(m.toByteArray(o1)).asString(StandardCharsets.UTF_8).isEqualTo(json1);
        assertThat(m.toByteArray(o2)).asString(StandardCharsets.UTF_8).isEqualTo(json2);
        assertThat(m.read(json1.getBytes(StandardCharsets.UTF_8))).isEqualTo(o1);
        assertThat(m.read(json2.getBytes(StandardCharsets.UTF_8))).isEqualTo(o2);
    }

    @Test
    public void testSealedInterfaceWithField() throws IOException {
        compile("""
            @Json
            @JsonDiscriminatorField("@type")
            public sealed interface TestInterface {
                @Json
                @JsonDiscriminatorValue({"Impl1.1", "Impl1.2"})
                record Impl1(@JsonField("@type") String type, String value) implements TestInterface {
                    public Impl1 {
                        if (!"Impl1.1".equals(type) && !"Impl1.2".equals(type)) {
                          throw new IllegalStateException(String.valueOf(type));
                        }
                    }
                }

                @Json
                record Impl2(int value) implements TestInterface{}

                @Json
                @JsonDiscriminatorValue({"Impl3.1", "Impl3.2"})
                record Impl3() implements TestInterface {
                }
            }
            """);

        var o11 = newObject("TestInterface$Impl1", "Impl1.1", "test");
        var json11 = "{\"@type\":\"Impl1.1\",\"value\":\"test\"}";
        var o12 = newObject("TestInterface$Impl1", "Impl1.2", "test");
        var json12 = "{\"@type\":\"Impl1.2\",\"value\":\"test\"}";

        var o2 = newObject("TestInterface$Impl2", 42);
        var json2 = "{\"@type\":\"Impl2\",\"value\":42}";

        var o3 = newObject("TestInterface$Impl3");
        var json31 = "{\"@type\":\"Impl3.1\"}";
        var json32 = "{\"@type\":\"Impl3.2\"}";

        var m1 = mapper("TestInterface_Impl1");
        var m2 = mapper("TestInterface_Impl2");
        var m3 = mapper("TestInterface_Impl3");
        var m = mapper("TestInterface", List.of(m1, m2, m3), List.of(m1, m2, m3));

        assertThat(m.toByteArray(o11)).asString(StandardCharsets.UTF_8).isEqualTo(json11);
        assertThat(m.toByteArray(o12)).asString(StandardCharsets.UTF_8).isEqualTo(json12);
        assertThat(m.toByteArray(o2)).asString(StandardCharsets.UTF_8).isEqualTo(json2);
        assertThat(m.toByteArray(o3)).asString(StandardCharsets.UTF_8).isEqualTo(json31);
        assertThat(m.read(json11.getBytes(StandardCharsets.UTF_8))).isEqualTo(o11);
        assertThat(m.read(json12.getBytes(StandardCharsets.UTF_8))).isEqualTo(o12);
        assertThat(m.read(json2.getBytes(StandardCharsets.UTF_8))).isEqualTo(o2);
        assertThat(m.read(json31.getBytes(StandardCharsets.UTF_8))).isEqualTo(o3);
        assertThat(m.read(json32.getBytes(StandardCharsets.UTF_8))).isEqualTo(o3);
    }

    @Test
    public void testSealedInterfaceParsingType() throws IOException {
        compile("""
            @Json
            @JsonDiscriminatorField("@type")
            public sealed interface TestInterface {
                @Json
                record Impl1(String value) implements TestInterface{}
                @Json
                record Impl2(int value) implements TestInterface{}
            }
            """);
        var o1 = newObject("TestInterface$Impl1", "test");
        var json1 = "{\"value\":\"test\", \"@type\":\"Impl1\"}";
        var o2 = newObject("TestInterface$Impl2", 42);
        var json2 = "{\"value\":42, \"@type\":\"Impl2\"}";

        var m1 = mapper("TestInterface_Impl1");
        var m2 = mapper("TestInterface_Impl2");
        var m = mapper("TestInterface", List.of(m1, m2), List.of(m1, m2));

        assertThat(m.read(json1.getBytes(StandardCharsets.UTF_8))).isEqualTo(o1);
        assertThat(m.read(json2.getBytes(StandardCharsets.UTF_8))).isEqualTo(o2);
    }

    @Test
    public void testSealedAbstractClass() throws IOException {
        compile("""
            @Json
            @JsonDiscriminatorField("@type")
            sealed abstract public class TestInterface {
                @Json
                public static final class Impl1 extends TestInterface {
                  private final String value;
                  public Impl1(String value) {
                    this.value = value;
                  }
                  public String value() { return value; }
                  public boolean equals(Object obj) { return obj instanceof Impl1 i && i.value.equals(value); }
                }
                @Json
                public static final class Impl2 extends TestInterface {
                  private final int value;
                  public Impl2(int value) {
                    this.value = value;
                  }
                  public int value() { return value; }
                  public boolean equals(Object obj) { return obj instanceof Impl2 i && i.value == value; }
                }
            }
            """);
        var o1 = newObject("TestInterface$Impl1", "test");
        var json1 = "{\"@type\":\"Impl1\",\"value\":\"test\"}";
        var o2 = newObject("TestInterface$Impl2", 42);
        var json2 = "{\"@type\":\"Impl2\",\"value\":42}";

        var m1 = mapper("TestInterface_Impl1");
        var m2 = mapper("TestInterface_Impl2");
        var m = mapper("TestInterface", List.of(m1, m2), List.of(m1, m2));

        assertThat(m.toByteArray(o1)).asString(StandardCharsets.UTF_8).isEqualTo(json1);
        assertThat(m.toByteArray(o2)).asString(StandardCharsets.UTF_8).isEqualTo(json2);
        assertThat(m.read(json1.getBytes(StandardCharsets.UTF_8))).isEqualTo(o1);
        assertThat(m.read(json2.getBytes(StandardCharsets.UTF_8))).isEqualTo(o2);
    }

    @Test
    public void testSealedSubinterfaces() throws IOException {
        compile("""
            @Json
            @JsonDiscriminatorField("@type")
            public sealed interface TestInterface {
                sealed interface Subinterface extends TestInterface {}
                @Json
                record Impl1(String value) implements Subinterface {}
                @Json
                record Impl2(int value) implements Subinterface {}
            }
            """);
        var o1 = newObject("TestInterface$Impl1", "test");
        var json1 = "{\"@type\":\"Impl1\",\"value\":\"test\"}";
        var o2 = newObject("TestInterface$Impl2", 42);
        var json2 = "{\"@type\":\"Impl2\",\"value\":42}";

        var m1 = mapper("TestInterface_Impl1");
        var m2 = mapper("TestInterface_Impl2");
        var m = mapper("TestInterface", List.of(m1, m2), List.of(m1, m2));

        assertThat(m.toByteArray(o1)).asString(StandardCharsets.UTF_8).isEqualTo(json1);
        assertThat(m.toByteArray(o2)).asString(StandardCharsets.UTF_8).isEqualTo(json2);
        assertThat(m.read(json1.getBytes(StandardCharsets.UTF_8))).isEqualTo(o1);
        assertThat(m.read(json2.getBytes(StandardCharsets.UTF_8))).isEqualTo(o2);
    }

    @Test
    public void testExplicitDiscriminator() throws IOException {
        compile("""
            @Json
            @JsonDiscriminatorField("@type")
            public sealed interface TestInterface {
                @JsonDiscriminatorValue("type_1")
                @Json
                record Impl1(String value) implements TestInterface {}
                @Json
                record Impl2(int value) implements TestInterface {}
            }
            """);
        var o1 = newObject("TestInterface$Impl1", "test");
        var json1 = "{\"@type\":\"type_1\",\"value\":\"test\"}";
        var o2 = newObject("TestInterface$Impl2", 42);
        var json2 = "{\"@type\":\"Impl2\",\"value\":42}";

        var m1 = mapper("TestInterface_Impl1");
        var m2 = mapper("TestInterface_Impl2");
        var m = mapper("TestInterface", List.of(m1, m2), List.of(m1, m2));

        assertThat(m.toByteArray(o1)).asString(StandardCharsets.UTF_8).isEqualTo(json1);
        assertThat(m.toByteArray(o2)).asString(StandardCharsets.UTF_8).isEqualTo(json2);
        assertThat(m.read(json1.getBytes(StandardCharsets.UTF_8))).isEqualTo(o1);
        assertThat(m.read(json2.getBytes(StandardCharsets.UTF_8))).isEqualTo(o2);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSealedInterfaceJsonReaderExtension() throws IOException, ClassNotFoundException {
        compile(List.of(new JsonAnnotationProcessor(), new KoraAppProcessor()), """
                @Json
                @JsonDiscriminatorField("@type")
                public sealed interface TestInterface {
                    record Impl1(String value) implements TestInterface {}
                    record Impl2(int value) implements TestInterface {}
                }
                """,
            """
                @KoraApp
                public interface TestApp {
                    @Root
                    default String root(ru.tinkoff.kora.json.common.JsonReader<TestInterface> writer) { return ""; }
                }
                    """);
        compileResult.assertSuccess();
        var supplier = (Supplier<ApplicationGraphDraw>) newObject("TestAppGraph");
        var draw = supplier.get();
        var graph = draw.init().block();
        var rc = compileResult.loadClass("$TestInterfaceJsonReader");
        JsonReader<Object> reader = null;
        for (var node : draw.getNodes()) {
            var object = graph.get(node);
            if (rc.isInstance(object)) {
                reader = (JsonReader<Object>) object;
            }
        }

        var o1 = newObject("TestInterface$Impl1", "test");
        var json1 = "{\"@type\":\"Impl1\",\"value\":\"test\"}";
        var o2 = newObject("TestInterface$Impl2", 42);
        var json2 = "{\"@type\":\"Impl2\",\"value\":42}";

        assertThat(reader.read(json1.getBytes(StandardCharsets.UTF_8))).isEqualTo(o1);
        assertThat(reader.read(json2.getBytes(StandardCharsets.UTF_8))).isEqualTo(o2);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSealedInterfaceWriterJsonExtension() throws IOException, ClassNotFoundException {
        compile(List.of(new JsonAnnotationProcessor(), new KoraAppProcessor()), """
                @Json
                @JsonDiscriminatorField("@type")
                public sealed interface TestInterface {
                    record Impl1(String value) implements TestInterface {}
                    record Impl2(int value) implements TestInterface {}
                }
                """,
            """
                @KoraApp
                public interface TestApp {
                    @Root
                    default String root(ru.tinkoff.kora.json.common.JsonWriter<TestInterface> writer) { return ""; }
                }
                    """);
        compileResult.assertSuccess();
        var supplier = (Supplier<ApplicationGraphDraw>) newObject("TestAppGraph");
        var draw = supplier.get();
        var graph = draw.init().block();
        var wc = compileResult.loadClass("$TestInterfaceJsonWriter");
        JsonWriter<Object> writer = null;
        for (var node : draw.getNodes()) {
            var object = graph.get(node);
            if (wc.isInstance(object)) {
                writer = (JsonWriter<Object>) object;
            }
        }

        var o1 = newObject("TestInterface$Impl1", "test");
        var json1 = "{\"@type\":\"Impl1\",\"value\":\"test\"}";
        var o2 = newObject("TestInterface$Impl2", 42);
        var json2 = "{\"@type\":\"Impl2\",\"value\":42}";

        assertThat(writer.toByteArray(o1)).asString(StandardCharsets.UTF_8).isEqualTo(json1);
        assertThat(writer.toByteArray(o2)).asString(StandardCharsets.UTF_8).isEqualTo(json2);
    }
}
