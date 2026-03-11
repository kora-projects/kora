package io.koraframework.validation.annotation.processor;

import org.junit.jupiter.api.Test;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ValidationExtensionTest extends AbstractValidationAnnotationProcessorTest {

    @Test
    public void testExtension() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
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
}
