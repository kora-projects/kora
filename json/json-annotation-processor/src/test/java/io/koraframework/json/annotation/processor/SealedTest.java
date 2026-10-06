package io.koraframework.json.annotation.processor;

import io.koraframework.annotation.processor.common.JavaCompilation;
import io.koraframework.json.common.JsonReader;
import io.koraframework.json.common.JsonWriter;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JsonParser;

import javax.tools.Diagnostic;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

public class SealedTest extends AbstractJsonAnnotationProcessorTest {

    @Test
    public void testJsonSealedInterface() throws IOException {
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
    public void testJsonReaderSealedInterface() {
        compile("""
            @JsonReader
            @JsonDiscriminatorField("@type")
            public sealed interface TestInterface {
                @Json
                record Impl1(String value) implements TestInterface{}
                @Json
                record Impl2(int value) implements TestInterface{}
            }
            """);

        var m1 = reader("TestInterface_Impl1");
        assertThat(m1).isNotNull();
        var m2 = reader("TestInterface_Impl2");
        assertThat(m2).isNotNull();
        var m = reader("TestInterface", m1, m2);
        assertThat(m).isNotNull();
    }

    @Test
    public void testJsonWriterSealedInterface() throws IOException {
        compile("""
            @JsonWriter
            @JsonDiscriminatorField("@type")
            public sealed interface TestInterface {
                @Json
                record Impl1(String value) implements TestInterface{}
                @Json
                record Impl2(int value) implements TestInterface{}
            }
            """);

        var m1 = writer("TestInterface_Impl1");
        assertThat(m1).isNotNull();
        var m2 = writer("TestInterface_Impl2");
        assertThat(m2).isNotNull();
        var m = writer("TestInterface", m1, m2);
        assertThat(m).isNotNull();
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
    public void testSealedInterfaceWithDefaultDiscriminator() throws IOException {
        compile("""
            @Json
            @JsonDiscriminatorField(value = "@type", defaultValue = "Impl1")
            public sealed interface TestInterface {
                @Json
                record Impl1(String value) implements TestInterface{}
                @Json
                record Impl2(int value) implements TestInterface{}
            }
            """);
        var o1 = newObject("TestInterface$Impl1", "test");
        var json1 = "{\"value\":\"test\"}";
        var o2 = newObject("TestInterface$Impl2", 42);
        var json2 = "{\"@type\":\"Impl2\",\"value\":42}";

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
    public void testSealedNull() throws IOException {
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

        var m1 = mapper("TestInterface_Impl1");
        var m2 = mapper("TestInterface_Impl2");
        var m = mapper("TestInterface", List.of(m1, m2), List.of(m1, m2));

        assertThat(m.read("null")).isNull();
    }

    @Test
    public void testGenericSealedInterfaceIsLintClean() throws Exception {
        compile("""
            @Json
            @JsonDiscriminatorField("type")
            public sealed interface Result<T> {
                @Json
                record Ok<T>(T value) implements Result<T> {}
                @Json
                record Fail<T>(String error) implements Result<T> {}
            }
            """);
        var ok = newObject("Result$Ok", "test");
        var okJson = "{\"type\":\"Ok\",\"value\":\"test\"}";
        var fail = newObject("Result$Fail", "boom");
        var failJson = "{\"type\":\"Fail\",\"error\":\"boom\"}";
        JsonReader<Object> stringReader = JsonParser::getString;
        JsonWriter<Object> stringWriter = (gen, v) -> gen.writeString((String) v);
        var okMapper = mapper("Result_Ok", List.of(stringReader), List.of(stringWriter));
        var failMapper = mapper("Result_Fail");
        var m = mapper("Result", List.of(okMapper, failMapper), List.of(okMapper, failMapper));

        assertThat(m.toByteArray(ok)).asString(StandardCharsets.UTF_8).isEqualTo(okJson);
        assertThat(m.toByteArray(fail)).asString(StandardCharsets.UTF_8).isEqualTo(failJson);
        assertThat(m.read(okJson.getBytes(StandardCharsets.UTF_8))).isEqualTo(ok);
        assertThat(m.read(failJson.getBytes(StandardCharsets.UTF_8))).isEqualTo(fail);

        assertThat(lintWarnings("Result")).isEmpty();
    }

    @Test
    public void testGenericSealedInterfaceWithNarrowedSubtypesIsLintClean() throws Exception {
        compile("""
            @Json
            @JsonDiscriminatorField("@type")
            public sealed interface Pair<A, B> {
                @Json
                record Both<A, B>(A a, B b) implements Pair<A, B> {}
                @Json
                record Left<A>(A a) implements Pair<A, Object> {}
                @Json
                record Right<B>(B b) implements Pair<Object, B> {}
                @Json
                enum Empty implements Pair<String, String> { INSTANCE }
            }
            """);

        assertThat(lintWarnings("Pair")).isEmpty();
    }

    /**
     * Compiles the source written by the last {@link #compile} call again with {@code -Xlint:all}
     * and returns the warnings javac reports for the code the processor generated from it.
     */
    private List<String> lintWarnings(String sourceClass) throws Exception {
        var dir = Path.of("build", "in-test-generated", "lint");
        var source = Path.of("build", "in-test-generated", "sources").resolve(testPackage().replace('.', '/')).resolve(sourceClass + ".java");
        var jc = new JavaCompilation()
            .withSources(source)
            .withProcessors(List.of(new JsonAnnotationProcessor()))
            .withClassesDir(dir.resolve("classes"))
            .withGeneratedSourcesDir(dir.resolve("sources"))
            .withOption("-Xlint:all")
            .withOption("-Xlint:-processing");
        jc.compile();
        return jc.diagnostics().stream()
            .filter(d -> d.getKind() == Diagnostic.Kind.WARNING || d.getKind() == Diagnostic.Kind.MANDATORY_WARNING)
            .map(d -> d.getSource() + ":" + d.getLineNumber() + ": " + d.getMessage(Locale.ENGLISH))
            .toList();
    }
}
