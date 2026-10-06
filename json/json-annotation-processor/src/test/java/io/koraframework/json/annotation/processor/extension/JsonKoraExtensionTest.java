package io.koraframework.json.annotation.processor.extension;

import org.junit.jupiter.api.Test;
import io.koraframework.json.annotation.processor.AbstractJsonAnnotationProcessorTest;
import io.koraframework.json.annotation.processor.JsonAnnotationProcessor;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;

import io.koraframework.json.common.JsonReader;
import io.koraframework.json.common.JsonWriter;

import javax.tools.Diagnostic;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class JsonKoraExtensionTest extends AbstractJsonAnnotationProcessorTest {

    @Test
    public void testReaderFromExtensionNotFoundForInterface() {
        compile(List.of(new KoraAppProcessor()), """
            @io.koraframework.common.annotation.KoraApp
            public interface TestApp {
              interface TestInterface {}
            
              default String root0(io.koraframework.json.common.JsonReader<TestInterface> r) {return "";}
            
              @Root
              default Integer root1(String test) {return 42;}
            }
            """);

        assertThat(compileResult.isFailed()).isTrue();
        assertThat(compileResult.diagnostic())
            .anySatisfy(diagnostic -> assertMissingJsonMapperError(diagnostic, "JsonReader", "testReaderFromExtensionNotFoundForInterface"));
    }

    @Test
    public void testReaderFoundForAnnotatedRecord() {
        compile(List.of(new KoraAppProcessor(), new JsonAnnotationProcessor()), """
            @io.koraframework.common.annotation.KoraApp
            public interface TestApp {
              @io.koraframework.json.common.annotation.Json
              record TestRecord() {}
            
              @Root
              default Integer root1(io.koraframework.json.common.JsonReader<TestRecord> r) {return 42;}
            }
            """);
        compileResult.assertSuccess();
        var app = loadGraph("TestApp");
        assertThat(app.draw().getNodes()).hasSize(2);
    }

    @Test
    public void testWriterFromExtensionNotFoundForInterface() {
        compile(List.of(new KoraAppProcessor()), """
            @io.koraframework.common.annotation.KoraApp
            public interface TestApp {
              interface TestInterface {}
            
              @Root
              default String root(io.koraframework.json.common.JsonWriter<TestInterface> r) {return "";}
            }
            """);

        assertThat(compileResult.isFailed()).isTrue();
        assertThat(compileResult.diagnostic())
            .anySatisfy(diagnostic -> assertMissingJsonMapperError(diagnostic, "JsonWriter", "testWriterFromExtensionNotFoundForInterface"));
    }

    @Test
    public void testWriterFoundForAnnotatedRecord() {
        compile(List.of(new KoraAppProcessor(), new JsonAnnotationProcessor()), """
            @io.koraframework.common.annotation.KoraApp
            public interface TestApp {
              @io.koraframework.json.common.annotation.Json
              record TestRecord() {}
            
              @Root
              default Integer root1(io.koraframework.json.common.JsonWriter<TestRecord> r) {return 42;}
            }
            """);
        compileResult.assertSuccess();
        var app = loadGraph("TestApp");
        assertThat(app.draw().getNodes()).hasSize(2);
    }

    @Test
    public void testReaderFromExtensionGeneratedForSealedInterface() {
        compile(List.of(new KoraAppProcessor(), new JsonAnnotationProcessor()), """
            @io.koraframework.common.annotation.KoraApp
            public interface TestApp {
            
              @JsonDiscriminatorField("type")
              @io.koraframework.json.common.annotation.Json
              sealed interface TestInterface {
                @io.koraframework.json.common.annotation.Json
                record Impl1(String value) implements TestInterface { }
                @io.koraframework.json.common.annotation.Json
                record Impl2(int value) implements TestInterface { }
              }
            
              default String root0(io.koraframework.json.common.JsonReader<TestInterface> r) { return ""; }
            
              @Root
              default Integer root1(String test) { return 42; }
            }
            """);

        compileResult.assertSuccess();
        var app = loadGraph("TestApp");
        assertThat(app.draw().getNodes()).hasSize(5);
    }

    @Test
    public void testWriterFromExtensionGeneratedForSealedInterface() {
        compile(List.of(new KoraAppProcessor(), new JsonAnnotationProcessor()), """
            @io.koraframework.common.annotation.KoraApp
            public interface TestApp {
            
              @JsonDiscriminatorField("type")
              @io.koraframework.json.common.annotation.Json
              sealed interface TestInterface {
                @io.koraframework.json.common.annotation.Json
                record Impl1(String value) implements TestInterface { }
                @io.koraframework.json.common.annotation.Json
                record Impl2(int value) implements TestInterface { }
              }
            
              default String root0(io.koraframework.json.common.JsonWriter<TestInterface> r) { return ""; }
            
              @Root
              default Integer root1(String test) { return 42; }
            }
            """);

        compileResult.assertSuccess();
        var app = loadGraph("TestApp");
        assertThat(app.draw().getNodes()).hasSize(5);
    }

    @Test
    public void testDelegatingValueTypeAsField() throws Exception {
        compile(List.of(new KoraAppProcessor(), new JsonAnnotationProcessor()), """
            @io.koraframework.common.annotation.KoraApp
            public interface TestApp extends io.koraframework.json.common.JsonModule {
              record UserId(long id) {
                @io.koraframework.json.common.annotation.JsonReader
                public static UserId of(long value) { return new UserId(value); }
                @io.koraframework.json.common.annotation.JsonWriter
                public long id() { return id; }
              }

              @io.koraframework.json.common.annotation.Json
              record User(UserId id, java.util.List<UserId> friends) {}

              record Codec(Object r, Object w) {}

              @Root
              default Codec codec(io.koraframework.json.common.JsonReader<User> r, io.koraframework.json.common.JsonWriter<User> w) { return new Codec(r, w); }
            }
            """);
        compileResult.assertSuccess();

        assertThat(roundTrip("{\"id\":1,\"friends\":[2]}")).isEqualTo("{\"id\":1,\"friends\":[2]}");
    }

    @Test
    public void testDelegatingValueTypeAsRoot() throws Exception {
        compile(List.of(new KoraAppProcessor(), new JsonAnnotationProcessor()), """
            @io.koraframework.common.annotation.KoraApp
            public interface TestApp extends io.koraframework.json.common.JsonModule {
              record UserId(long id) {
                @io.koraframework.json.common.annotation.JsonReader
                public static UserId of(long value) { return new UserId(value); }
                @io.koraframework.json.common.annotation.JsonWriter
                public long id() { return id; }
              }

              record Codec(Object r, Object w) {}

              @Root
              default Codec codec(io.koraframework.json.common.JsonReader<UserId> r, io.koraframework.json.common.JsonWriter<UserId> w) { return new Codec(r, w); }
            }
            """);
        compileResult.assertSuccess();

        assertThat(roundTrip("42")).isEqualTo("42");
    }

    @SuppressWarnings("unchecked")
    private String roundTrip(String json) throws Exception {
        var codecClass = compileResult.loadClass("TestApp$Codec");
        var codec = loadGraph("TestApp").findByType(codecClass);
        var reader = (JsonReader<Object>) codecClass.getMethod("r").invoke(codec);
        var writer = (JsonWriter<Object>) codecClass.getMethod("w").invoke(codec);
        return new String(writer.toByteArray(reader.read(json.getBytes(StandardCharsets.UTF_8))), StandardCharsets.UTF_8);
    }

    private static void assertMissingJsonMapperError(Diagnostic<?> diagnostic, String mapperType, String testPackage) {
        assertThat(diagnostic.getKind()).isEqualTo(Diagnostic.Kind.ERROR);
        assertThat(diagnostic.getMessage(Locale.US))
            .contains("io.koraframework.json.common." + mapperType)
            .contains(testPackage)
            .contains("TestApp.TestInterface");
    }
}
