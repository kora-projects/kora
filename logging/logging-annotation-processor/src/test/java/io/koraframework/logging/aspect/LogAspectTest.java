package io.koraframework.logging.aspect;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import io.koraframework.aop.annotation.processor.AopAnnotationProcessor;
import io.koraframework.json.annotation.processor.JsonAnnotationProcessor;
import io.koraframework.json.common.JsonWriter;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;
import io.koraframework.logging.annotation.processor.LoggingAnnotationProcessor;
import io.koraframework.logging.common.arg.JsonStructuredArgumentMapper;
import io.koraframework.logging.common.arg.MaskedStructuredArgumentMapper;
import io.koraframework.logging.common.masking.MaskingMetadata;
import io.koraframework.logging.common.arg.StructuredArgumentWriter;
import io.koraframework.json.common.writer.ListJsonWriter;
import io.koraframework.json.common.writer.MapJsonWriter;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.slf4j.event.Level.*;

public class LogAspectTest extends AbstractLogAspectTest {

    @Test
    public void testLogPrintsInAndOut() {
        var aopProxy = compile("""
            public class Target {
              @Log
              public void test() {}
            }
            """);


        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        reset(log, INFO);
        aopProxy.invoke("test");
        var o = Mockito.inOrder(log);
        o.verify(log).info(">");
        o.verify(log).info("<");
        o.verifyNoMoreInteractions();
    }

    @Test
    public void testLogPrintsInAndOutWhenExceptionInWarn() {
        var aopProxy = compile("""
            public class Target {
              @Log
              public void test() {
                throw new RuntimeException("OPS");
              }
            }
            """);


        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        reset(log, INFO);
        mockLevel(log, WARN);
        var o = Mockito.inOrder(log);
        assertThrows(RuntimeException.class, () -> aopProxy.invoke("test"));
        o.verify(log).info(">");
        o.verify(log).warn(outData.capture(), eq("<"));
        verifyOutData(Map.of("errorType", "java.lang.RuntimeException",
            "errorMessage", "OPS"));
        o.verifyNoMoreInteractions();
    }

    @Test
    public void testLogPrintsInAndOutWhenExceptionInDebug() {
        var aopProxy = compile("""
            public class Target {
              @Log
              public void test() {
                throw new RuntimeException("OPS");
              }
            }
            """);


        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        reset(log, INFO);
        mockLevel(log, DEBUG);
        var o = Mockito.inOrder(log);
        assertThrows(RuntimeException.class, () -> aopProxy.invoke("test"));
        o.verify(log).info(">");
        o.verify(log).warn(outData.capture(), eq("<"), any(Throwable.class));
        verifyOutData(Map.of("errorType", "java.lang.RuntimeException",
            "errorMessage", "OPS"));
        o.verifyNoMoreInteractions();
    }

    @Test
    public void testLogInPrintsIn() {
        var aopProxy = compile("""
            public class Target {
              @Log.in
              public void test() {}
            }
            """);

        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        aopProxy.invoke("test");

        var o = Mockito.inOrder(log);
        o.verify(log).info(">");
        o.verifyNoMoreInteractions();
    }

    @Test
    public void testLogOutPrintsOut() {
        var aopProxy = compile("""
            public class Target {
              @Log.out
              public void test() {}
            }
            """);

        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        aopProxy.invoke("test");

        var o = Mockito.inOrder(log);
        o.verify(log).info("<");
        o.verifyNoMoreInteractions();
    }

    @Test
    public void testLogArgs() {
        var aopProxy = compile("""
            public class Target {
              @Log.in
              public void test(String arg1, @Log(TRACE) String arg2, @Log.off String arg3, int arg4, boolean arg5) {}
            }
            """);

        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        reset(log, INFO);
        aopProxy.invoke("test", "test1", "test2", "test3", 1, true);
        var o = Mockito.inOrder(log);
        o.verify(log).isDebugEnabled();
        o.verify(log).info(">");
        o.verifyNoMoreInteractions();

        reset(log, DEBUG);
        aopProxy.invoke("test", "test1", "test2", "test3", 1, true);
        o = Mockito.inOrder(log);
        o.verify(log).isDebugEnabled();
        o.verify(log).info(inData.capture(), eq(">"));
        o.verifyNoMoreInteractions();
        verifyInData(Map.of("arg1", "test1", "arg4", "1", "arg5", "true"));
        o.verify(log).isTraceEnabled();
        o.verifyNoMoreInteractions();

        reset(log, TRACE);
        aopProxy.invoke("test", "test1", "test2", "test3", 1, true);
        o = Mockito.inOrder(log);
        o.verify(log).isDebugEnabled();
        o.verify(log).info(inData.capture(), eq(">"));
        o.verifyNoMoreInteractions();
        verifyInData(Map.of("arg1", "test1", "arg2", "test2", "arg4", "1", "arg5", "true"));
        o.verify(log).isTraceEnabled();
        o.verifyNoMoreInteractions();
    }

    @Test
    public void testLogArgsSameLevelAsIn() {
        var aopProxy = compile("""
            public class Target {
              @Log.in
              public void test(@Log(INFO) String arg1, @Log(TRACE) String arg2, @Log.off String arg3) {}
            }
            """);

        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        reset(log, INFO);
        aopProxy.invoke("test", "test1", "test2", "test3");
        var o = Mockito.inOrder(log);
        o.verify(log).isInfoEnabled();
        o.verify(log).info(inData.capture(), eq(">"));
        o.verifyNoMoreInteractions();
        verifyInData(Map.of("arg1", "test1"));
        o.verify(log).isTraceEnabled();
        o.verifyNoMoreInteractions();
    }

    @Test
    public void testLogResults() {
        var aopProxy = compile("""
            public class Target {
              @Log.out
              public String test() { return "test-result"; }
            }
            """);

        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        reset(log, INFO);
        aopProxy.invoke("test");
        var o = Mockito.inOrder(log);
        o.verify(log).isDebugEnabled();
        o.verify(log).info("<");
        o.verifyNoMoreInteractions();

        reset(log, DEBUG);
        aopProxy.invoke("test");
        o = Mockito.inOrder(log);
        o.verify(log).isDebugEnabled();
        o.verify(log).info(outData.capture(), eq("<"));
        o.verifyNoMoreInteractions();
        verifyOutData(Map.of("out", "test-result"));
    }

    @Test
    public void testLogResultsWithExceptionInWarn() {
        var aopProxy = compile("""
            public class Target {
              @Log.out
              public String test() {
                throw new RuntimeException("OPS");
              }
            }
            """);

        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        reset(log, WARN);
        var o = Mockito.inOrder(log);
        assertThrows(RuntimeException.class, () -> aopProxy.invoke("test"));
        o.verify(log).isDebugEnabled();
        o.verify(log).warn(outData.capture(), eq("<"));
        verifyOutData(Map.of("errorType", "java.lang.RuntimeException",
            "errorMessage", "OPS"));
        o.verifyNoMoreInteractions();
    }

    @Test
    public void testLogResultsWithExceptionInDebug() {
        var aopProxy = compile("""
            public class Target {
              @Log.out
              public String test() {
                throw new RuntimeException("OPS");
              }
            }
            """);

        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        reset(log, DEBUG);
        var o = Mockito.inOrder(log);
        assertThrows(RuntimeException.class, () -> aopProxy.invoke("test"));
        o.verify(log).isDebugEnabled();
        o.verify(log).warn(outData.capture(), eq("<"), any(Throwable.class));
        verifyOutData(Map.of("errorType", "java.lang.RuntimeException",
            "errorMessage", "OPS"));
        o.verifyNoMoreInteractions();
    }

    @Test
    public void testLogResultsPrimitive() {
        var aopProxy = compile("""
            public class Target {
              @Log.out
              public int test() { return 1; }
            }
            """);

        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        reset(log, INFO);
        aopProxy.invoke("test");
        var o = Mockito.inOrder(log);
        o.verify(log).isDebugEnabled();
        o.verify(log).info("<");
        o.verifyNoMoreInteractions();

        reset(log, DEBUG);
        aopProxy.invoke("test");
        o = Mockito.inOrder(log);
        o.verify(log).isDebugEnabled();
        o.verify(log).info(outData.capture(), eq("<"));
        o.verifyNoMoreInteractions();
        verifyOutData(Map.of("out", "1"));
    }

    @Test
    public void testLogResultsOff() {
        var aopProxy = compile("""
            public class Target {
              @Log.out
              @Log.off
              public String test() { return "test-result"; }
            }
            """);

        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        reset(log, INFO);
        aopProxy.invoke("test");
        var o = Mockito.inOrder(log);
        o.verify(log).info("<");
        o.verifyNoMoreInteractions();

        reset(log, DEBUG);
        aopProxy.invoke("test");
        o = Mockito.inOrder(log);
        o.verify(log).info("<");
        o.verifyNoMoreInteractions();
    }

    @Test
    public void logResultSameLevelAsOut() {
        var aopProxy = compile("""
            public class Target {
              @Log.out
              @Log.result(INFO)
              public String test() { return "test-result"; }
            }
            """);

        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        reset(log, INFO);
        aopProxy.invoke("test");
        var o = Mockito.inOrder(log);
        o.verify(log).info(outData.capture(), eq("<"));
        o.verifyNoMoreInteractions();
        verifyOutData(Map.of("out", "test-result"));
    }

    @Test
    public void testLogArgsWithMapper() {
        compile(List.of(new AopAnnotationProcessor()), """
            public class Target {
              @Log.in
              public void test(@Mapping(MyLogArgMapper.class) String arg1) {}
            }
            """, """
            import io.koraframework.logging.common.arg.StructuredArgumentMapper;
            import tools.jackson.core.JsonGenerator;

            public final class MyLogArgMapper implements StructuredArgumentMapper<String> {
                public void write(JsonGenerator gen, String value) {
                  gen.writeString("mapped-" + value);
                }
            }
            """);
        compileResult.assertSuccess();
        var aopProxy = new TestObject(
            compileResult.loadClass("$Target__AopProxy"),
            newObject("$Target__AopProxy", factory, newObject("MyLogArgMapper"))
        );
        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        reset(log, DEBUG);
        aopProxy.invoke("test", "value");
        var o = Mockito.inOrder(log);
        o = Mockito.inOrder(log);
        o.verify(log).isDebugEnabled();
        o.verify(log).info(inData.capture(), eq(">"));
        o.verifyNoMoreInteractions();
        verifyInData(Map.of("arg1", "mapped-value"));
        o.verifyNoMoreInteractions();
    }

    @Test
    public void testLogArgsWithGenericMapper() {
        compile(List.of(new AopAnnotationProcessor()), """
            public class Target {
              @Log.in
              public void test(@Mapping(MyLogArgMapper.class) String arg1) {}
            }
            """, """
            import io.koraframework.logging.common.arg.StructuredArgumentMapper;
            import tools.jackson.core.JsonGenerator;

            public final class MyLogArgMapper <T> implements StructuredArgumentMapper<T> {
                public void write(JsonGenerator gen, T value) {
                  gen.writeString("mapped-" + value);
                }
            }
            """);
        compileResult.assertSuccess();
        var aopProxy = new TestObject(
            compileResult.loadClass("$Target__AopProxy"),
            newObject("$Target__AopProxy", factory, newObject("MyLogArgMapper"))
        );
        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        reset(log, DEBUG);
        aopProxy.invoke("test", "value");
        var o = Mockito.inOrder(log);
        o = Mockito.inOrder(log);
        o.verify(log).isDebugEnabled();
        o.verify(log).info(inData.capture(), eq(">"));
        o.verifyNoMoreInteractions();
        verifyInData(Map.of("arg1", "mapped-value"));
        o.verifyNoMoreInteractions();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void testLogArgsWithJsonMapperTag() {
        compile(List.of(new JsonAnnotationProcessor(), new LoggingAnnotationProcessor(), new AopAnnotationProcessor()), """
            @Json
            public record TestRecord(String value) {}
            """, """
            public class Target {
              @Log.in
              public void test(@Json TestRecord arg1) {}
            }
            """);
        compileResult.assertSuccess();
        var writer = (JsonWriter<Object>) newObject("$TestRecord_JsonWriter");
        var mapper = new JsonStructuredArgumentMapper<>(writer);
        var aopProxy = new TestObject(
            compileResult.loadClass("$Target__AopProxy"),
            newObject("$Target__AopProxy", factory, mapper)
        );

        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        reset(log, DEBUG);
        aopProxy.invoke("test", newObject("TestRecord", "test-value"));
        var o = Mockito.inOrder(log);
        o.verify(log).isDebugEnabled();
        o.verify(log).info(inData.capture(), eq(">"));
        o.verifyNoMoreInteractions();
        verifyInJson("{\"arg1\":{\"value\":\"test-value\"}}");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void testLogArgsWithMaskedMapperTag() {
        compile(List.of(new JsonAnnotationProcessor(), new LoggingAnnotationProcessor(), new AopAnnotationProcessor()), """
            @Mask(mode = Mask.Mode.KEEP_FIRST, keep = 1)
            @Json
            public record Credentials(@JsonField("secret") @Mask String secret, @Mask(mode = Mask.Mode.KEEP_LAST, keep = 2) String token, String login) {}
            """, """
            @Mask
            @Json
            public record User(String name, Credentials credentials) {}
            """, """
            public class Target {
              @Log.in
              public void test(@Mask User arg1) {}
            }
            """);
        compileResult.assertSuccess();
        var credentialsWriter = (JsonWriter<Object>) newObject("$Credentials_JsonWriter");
        var userWriter = (JsonWriter<Object>) newObject("$User_JsonWriter", credentialsWriter);
        var metadata = (MaskingMetadata<Object>) newObject("$User_MaskingMetadata");
        var mapper = new MaskedStructuredArgumentMapper<>(userWriter, metadata);
        var aopProxy = new TestObject(
            compileResult.loadClass("$Target__AopProxy"),
            newObject("$Target__AopProxy", factory, mapper)
        );

        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        var credentials = newObject("Credentials", "secret", "token", "login");
        var user = newObject("User", "user", credentials);
        reset(log, DEBUG);
        aopProxy.invoke("test", user);
        var o = Mockito.inOrder(log);
        o.verify(log).isDebugEnabled();
        o.verify(log).info(inData.capture(), eq(">"));
        o.verifyNoMoreInteractions();
        verifyInJson("{\"arg1\":{\"name\":\"user\",\"credentials\":{\"secret\":\"s***\",\"token\":\"***en\",\"login\":\"login\"}}}");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void testLogResultWithJsonMapperTag() {
        compile(List.of(new JsonAnnotationProcessor(), new LoggingAnnotationProcessor(), new AopAnnotationProcessor()), """
            @Json
            public record TestRecord(String value) {}
            """, """
            public class Target {
              @Log.out
              @Json
              public TestRecord test() {
                return new TestRecord("test-value");
              }
            }
            """);
        compileResult.assertSuccess();
        var writer = (JsonWriter<Object>) newObject("$TestRecord_JsonWriter");
        var mapper = new JsonStructuredArgumentMapper<>(writer);
        var aopProxy = new TestObject(
            compileResult.loadClass("$Target__AopProxy"),
            newObject("$Target__AopProxy", factory, mapper)
        );

        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        reset(log, DEBUG);
        aopProxy.invoke("test");
        var o = Mockito.inOrder(log);
        o.verify(log).isDebugEnabled();
        o.verify(log).info(outData.capture(), eq("<"));
        o.verifyNoMoreInteractions();
        verifyOutJson("{\"out\":{\"value\":\"test-value\"}}");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void testLogResultWithMaskedMapperTag() {
        compile(List.of(new JsonAnnotationProcessor(), new LoggingAnnotationProcessor(), new AopAnnotationProcessor()), """
            @Mask
            @Json
            public record User(String name, @Mask(mode = Mask.Mode.KEEP_LAST, keep = 2) String token) {}
            """, """
            public class Target {
              @Log.out
              @Mask
              public User test() {
                return new User("user", "secret");
              }
            }
            """);
        compileResult.assertSuccess();
        var writer = (JsonWriter<Object>) newObject("$User_JsonWriter");
        var metadata = (MaskingMetadata<Object>) newObject("$User_MaskingMetadata");
        var mapper = new MaskedStructuredArgumentMapper<>(writer, metadata);
        var aopProxy = new TestObject(
            compileResult.loadClass("$Target__AopProxy"),
            newObject("$Target__AopProxy", factory, mapper)
        );

        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        reset(log, DEBUG);
        aopProxy.invoke("test");
        var o = Mockito.inOrder(log);
        o.verify(log).isDebugEnabled();
        o.verify(log).info(outData.capture(), eq("<"));
        o.verifyNoMoreInteractions();
        verifyOutJson("{\"out\":{\"name\":\"user\",\"token\":\"***et\"}}");
    }

    @Test
    public void testMaskingMetadataSupportsRecursiveType() {
        compile(List.of(new JsonAnnotationProcessor(), new LoggingAnnotationProcessor()), """
            @Mask
            @Json
            public record User(String name, @Mask String token, User manager) {}
            """);
        compileResult.assertSuccess();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void testMaskingMetadataSupportsNestedGenericContainers() {
        compile(List.of(new JsonAnnotationProcessor(), new LoggingAnnotationProcessor(), new AopAnnotationProcessor()), """
            @Mask
            @Json
            public record Credentials(@Mask String secret) {}
            """, """
            @Mask
            @Json
            public record User(java.util.List<java.util.List<Credentials>> nestedList, java.util.Map<String, java.util.List<Credentials>> nestedMap) {}
            """, """
            public class Target {
              @Log.in
              public void test(@Mask User arg1) {}
            }
            """);
        compileResult.assertSuccess();
        var credentialsWriter = (JsonWriter<Object>) newObject("$Credentials_JsonWriter");
        var listWriter = new ListJsonWriter<>(credentialsWriter);
        var nestedListWriter = new ListJsonWriter<>(listWriter);
        var nestedMapWriter = new MapJsonWriter<>(listWriter);
        var userWriter = (JsonWriter<Object>) newObject("$User_JsonWriter", nestedListWriter, nestedMapWriter);
        var metadata = (MaskingMetadata<Object>) newObject("$User_MaskingMetadata");
        var mapper = new MaskedStructuredArgumentMapper<>(userWriter, metadata);
        var aopProxy = new TestObject(
            compileResult.loadClass("$Target__AopProxy"),
            newObject("$Target__AopProxy", factory, mapper)
        );

        verify(factory).getLogger(testPackage() + ".Target.test");
        var log = Objects.requireNonNull(loggers.get(testPackage() + ".Target.test"));

        var credentials1 = newObject("Credentials", "secret-1");
        var credentials2 = newObject("Credentials", "secret-2");
        var user = newObject("User", List.of(List.of(credentials1)), Map.of("key", List.of(credentials2)));
        reset(log, DEBUG);
        aopProxy.invoke("test", user);
        var o = Mockito.inOrder(log);
        o.verify(log).isDebugEnabled();
        o.verify(log).info(inData.capture(), eq(">"));
        o.verifyNoMoreInteractions();
        verifyInJson("{\"arg1\":{\"nestedList\":[[{\"secret\":\"***\"}]],\"nestedMap\":{\"key\":[{\"secret\":\"***\"}]}}}");
    }

    @Test
    public void testMaskingMetadataComponentIsResolvedByGraph() {
        compile(List.of(new JsonAnnotationProcessor(), new LoggingAnnotationProcessor(), new KoraAppProcessor()), """
            @Mask
            @Json
            public record User(@Mask String password) {}
            """, """
            @KoraApp
            public interface TestApp extends io.koraframework.json.common.JsonModule {
              @Tag(Mask.class)
              default <T> io.koraframework.logging.common.arg.StructuredArgumentMapper<T> maskedStructuredArgumentMapper(
                  io.koraframework.json.common.JsonWriter<T> writer,
                  io.koraframework.logging.common.masking.MaskingMetadata<T> metadata
              ) {
                return new io.koraframework.logging.common.arg.MaskedStructuredArgumentMapper<>(writer, metadata);
              }

              @Root
              default Object root(@Tag(Mask.class) io.koraframework.logging.common.arg.StructuredArgumentMapper<User> mapper) {
                return mapper;
              }
            }
            """);
        compileResult.assertSuccess();
    }

    private void verifyInJson(String expectedJson) {
        var writer = (StructuredArgumentWriter) inData.getValue();
        org.assertj.core.api.Assertions.assertThat(writer.writeToString()).isEqualTo(expectedJson);
    }

    private void verifyOutJson(String expectedJson) {
        var writer = (StructuredArgumentWriter) outData.getValue();
        org.assertj.core.api.Assertions.assertThat(writer.writeToString()).isEqualTo(expectedJson);
    }

}
