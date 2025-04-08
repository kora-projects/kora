package ru.tinkoff.kora.logging.aspect.mdc;

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import ru.tinkoff.kora.annotation.processor.common.CompileResult;
import ru.tinkoff.kora.aop.annotation.processor.AopAnnotationProcessor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MdcAspectUnsupportedTypesTest extends AbstractMdcAspectTest {

    @ParameterizedTest
    @MethodSource({"sourcesWithMdcAndFuture", "sourcesWithMdcAndMono", "sourcesWithMdcAndFlux"})
    void testMdc(@Language("java") String source) {
        final CompileResult compileResult = compile(
            List.of(new AopAnnotationProcessor()),
            source
        );

        assertTrue(compileResult.isFailed());
    }

    private static List<String> sourcesWithMdcAndFuture() {
        return sources(
            """
                public class TestMdc {
                
                  @Mdc(key = "key", value = "value", global = true)
                  @Mdc(key = "key1", value = "value2")
                  public CompletionStage<?> test(@Mdc(key = "123", value = "value3") String s) {
                      return CompletableFuture.completedFuture(1);
                  }
                }
                """,
            """
                public class TestMdc {
                
                  @Mdc(key = "key1", value = "value2")
                  public CompletionStage<?> test(String s) {
                      return CompletableFuture.completedFuture(1);
                  }
                }
                """,
            """
                public class TestMdc {
                
                  public CompletionStage<?> test(@Mdc(key = "123") String s) {
                      return CompletableFuture.completedFuture(1);
                  }
                }
                """
        );
    }

    private static List<String> sourcesWithMdcAndMono() {
        return sources(
            """
                public class TestMdc {
                
                  @Mdc(key = "key", value = "value", global = true)
                  @Mdc(key = "key1", value = "value2")
                  public Mono<?> test(@Mdc(key = "123", value = "value3") String s) {
                      return Mono.just(1);
                  }
                }
                """,
            """
                public class TestMdc {
                
                  @Mdc(key = "key1", value = "value2")
                  public Mono<?> test(String s) {
                      return Mono.just(1);
                  }
                }
                """,
            """
                public class TestMdc {
                
                  public Mono<?> test(@Mdc(key = "123") String s) {
                      return Mono.just(1);
                  }
                }
                """
        );
    }

    private static List<String> sourcesWithMdcAndFlux() {
        return sources(
            """
                public class TestMdc {
                
                  @Mdc(key = "key", value = "value", global = true)
                  @Mdc(key = "key1", value = "value2")
                  public Flux<?> test(@Mdc(key = "123", value = "value3") String s) {
                      return Mono.just(1)
                          .flux();
                  }
                }
                """,
            """
                public class TestMdc {
                
                  @Mdc(key = "key1", value = "value2")
                  public Flux<?> test(String s) {
                      return Mono.just(1)
                          .flux();
                  }
                }
                """,
            """
                public class TestMdc {
                
                  public Flux<?> test(@Mdc(key = "123") String s) {
                      return Mono.just(1)
                          .flux();
                  }
                }
                """
        );
    }
}
