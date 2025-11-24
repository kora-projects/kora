package ru.tinkoff.kora.logging.aspect.mdc;

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import ru.tinkoff.kora.annotation.processor.common.CompileResult;
import ru.tinkoff.kora.aop.annotation.processor.AopAnnotationProcessor;
import ru.tinkoff.kora.logging.common.MDC;
import ru.tinkoff.kora.logging.common.arg.StructuredArgumentWriter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.*;

class MdcAspectTest extends AbstractMdcAspectTest {

    private static final MDCContextHolder CONTEXT_HOLDER = new MDCContextHolder();

    @ParameterizedTest
    @MethodSource("provideTestCases")
    void testMdc(@Language("java") String source) throws Exception {
        var aopProxy = compile(List.of(new AopAnnotationProcessor()), source);

        ScopedValue.where(MDC.VALUE, new MDC()).call(() -> {
            invokeMethod(aopProxy);

            final Map<String, String> context = extractMdcContextFromHolder();
            assertEquals(Map.of("key", "\"value\"", "key1", "\"value2\"", "123", "\"test\""), context);
            assertEquals(emptyMap(), currentMdcContext());

            return null;
        });
    }

    @Test
    void testMdcWithCode() throws Exception {
        var aopProxy = compile(
            List.of(new AopAnnotationProcessor()),
            """
                public class TestMdc {
                
                  private final MDCContextHolder mdcContextHolder;
                
                  public TestMdc(MDCContextHolder mdcContextHolder) {
                      this.mdcContextHolder = mdcContextHolder;
                  }
                
                  @Mdc(key = "key", value = "${java.util.UUID.randomUUID().toString()}")
                  public Integer test(String s) {
                      mdcContextHolder.set(MDC.get().values());
                      return null;
                  }
                }
                """
        );

        ScopedValue.where(MDC.VALUE, new MDC()).call(() -> {
            invokeMethod(aopProxy);
            final Map<String, String> context = extractMdcContextFromHolder();
            final String value = context.get("key");
            assertNotNull(value);
            assertDoesNotThrow(() -> UUID.fromString(value.substring(1, value.length() - 1)));
            assertEquals(emptyMap(), currentMdcContext());
            return null;
        });
    }

    @Test
    void testGlobalMdc() throws Exception {
        var aopProxy = compile(
            List.of(new AopAnnotationProcessor()),
            """
                public class TestMdc {
                
                  private final MDCContextHolder mdcContextHolder;
                
                  public TestMdc(MDCContextHolder mdcContextHolder) {
                      this.mdcContextHolder = mdcContextHolder;
                  }
                
                  @Mdc(key = "key", value = "value", global = true)
                  @Mdc(key = "key1", value = "value2")
                  public Integer test(@Mdc(key = "123", global = true) String s) {
                      mdcContextHolder.set(MDC.get().values());
                      return null;
                  }
                }
                """
        );

        ScopedValue.where(MDC.VALUE, new MDC()).call(() -> {
            invokeMethod(aopProxy);
            final Map<String, String> context = extractMdcContextFromHolder();
            assertEquals(Map.of("key", "\"value\"", "key1", "\"value2\"", "123", "\"test\""), context);
            assertEquals(Map.of("key", "\"value\"", "123", "\"test\""), currentMdcContext());
            return null;
        });

    }

    @Test
    void testMdcWithNotEmptyContext() throws Exception {
        var aopProxy = compile(
            List.of(new AopAnnotationProcessor()),
            """
                public class TestMdc {
                
                  private final MDCContextHolder mdcContextHolder;
                
                  public TestMdc(MDCContextHolder mdcContextHolder) {
                      this.mdcContextHolder = mdcContextHolder;
                  }
                
                  @Mdc(key = "key", value = "value")
                  @Mdc(key = "key1", value = "value2")
                  public Integer test(@Mdc(key = "123") String s) {
                      mdcContextHolder.set(MDC.get().values());
                      return null;
                  }
                }
                """
        );

        ScopedValue.where(MDC.VALUE, new MDC()).call(() -> {
            MDC.put("key", "special-value");
            MDC.put("123", "special-123");

            invokeMethod(aopProxy);
            final Map<String, String> context = extractMdcContextFromHolder();
            assertEquals(Map.of("key", "\"value\"", "key1", "\"value2\"", "123", "\"test\""), context);
            assertEquals(Map.of("key", "\"special-value\"", "123", "\"special-123\""), currentMdcContext());
            return null;
        });
    }

    private static List<String> provideTestCases() {
        return sources(
            """
                public class TestMdc {
                
                  private final MDCContextHolder mdcContextHolder;
                
                  public TestMdc(MDCContextHolder mdcContextHolder) {
                      this.mdcContextHolder = mdcContextHolder;
                  }
                
                  @Mdc(key = "key", value = "value")
                  @Mdc(key = "key1", value = "value2")
                  public Integer test(@Mdc(key = "123") String s) {
                      mdcContextHolder.set(MDC.get().values());
                      return null;
                  }
                }
                """,
            """
                public class TestMdc {
                
                  private final MDCContextHolder mdcContextHolder;
                
                  public TestMdc(MDCContextHolder mdcContextHolder) {
                      this.mdcContextHolder = mdcContextHolder;
                  }
                
                  @Mdc(key = "key", value = "value")
                  @Mdc(key = "key1", value = "value2")
                  public void test(@Mdc("123") String s) {
                      mdcContextHolder.set(MDC.get().values());
                  }
                }
                """
        );
    }

    private static void invokeMethod(CompileResult aopProxy) throws Exception {
        aopProxy.assertSuccess();

        var generatedClass = aopProxy.loadClass("$TestMdc__AopProxy");
        var constructor = generatedClass.getConstructors()[0];
        var params = new Object[constructor.getParameterCount()];
        params[0] = CONTEXT_HOLDER;
        final TestObject testObject = new TestObject(generatedClass, constructor.newInstance(params));

        testObject.invoke("test", "test");
    }

    private static Map<String, String> extractMdcContextFromHolder() {
        return toMdcContext(CONTEXT_HOLDER.get());
    }

    private static Map<String, String> currentMdcContext() {
        return toMdcContext(MDC.get().values());
    }

    private static Map<String, String> toMdcContext(Map<String, StructuredArgumentWriter> values) {
        return values.entrySet()
            .stream()
            .collect(toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().writeToString()
            ));
    }
}
