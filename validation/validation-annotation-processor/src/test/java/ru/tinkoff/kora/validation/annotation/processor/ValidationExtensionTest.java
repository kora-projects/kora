package ru.tinkoff.kora.validation.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ValidationExtensionTest extends AbstractValidationAnnotationProcessorTest {

    @Test
    public void testExtension() throws Exception {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                @Valid
                public record TestRecord(@Size(min = 1, max = 5) java.util.List<String> list){}
                """, """
                @KoraApp
                public interface TestApp extends ValidatorModule{
                   @Root
                   default String root(Validator<TestRecord> testRecordValidator) { return "";}
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();
        var graph = compileResult.loadClass("TestAppGraph");
        assertThat(graph).isNotNull();
    }

    @Test
    public void testExtensionNoAnnotationProcessor() throws Exception {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                public record TestRecord(@Size(min = 1, max = 5) java.util.List<String> list){}
                """, """
                @KoraApp
                public interface TestApp extends ValidatorModule{
                   @Root
                   default String root(Validator<TestRecord> testRecordValidator) { return "";}
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();
        var graph = compileResult.loadClass("TestAppGraph");
        assertThat(graph).isNotNull();
    }
}
