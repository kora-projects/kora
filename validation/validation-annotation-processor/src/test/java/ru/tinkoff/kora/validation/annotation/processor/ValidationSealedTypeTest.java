package ru.tinkoff.kora.validation.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;
import ru.tinkoff.kora.validation.common.Validator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ValidationSealedTypeTest extends AbstractValidationAnnotationProcessorTest {
    @Test
    public void testSealedInterface() throws Exception {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                @Valid
                public sealed interface TestInterface {
                  @Valid
                  record TestRecord(@Size(min = 1, max = 5) java.util.List<String> list) implements TestInterface {}
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestInterface_Validator");
        assertThat(validatorClass).isNotNull();
        assertThat(validatorClass.getConstructors()).hasSize(1);

        var constructor = validatorClass.getConstructors()[0];
        var parameters = constructor.getParameterTypes();
        assertThat(parameters).containsExactly(Validator.class);

        assertThat(constructor.getGenericParameterTypes()).containsExactly(TypeRef.of(Validator.class, compileResult.loadClass("TestInterface$TestRecord")));
    }

    @Test
    public void testExtensionForProcessedType() throws Exception {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                @Valid
                public sealed interface TestInterface {
                  @Valid
                  record TestRecord(@Size(min = 1, max = 5) java.util.List<String> list) implements TestInterface {}
                }
                """, """
                @KoraApp
                public interface TestApp extends ValidatorModule{
                   @Root
                   default String root(Validator<TestInterface> testRecordValidator) { return "";}
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestInterface_Validator");
        assertThat(validatorClass).isNotNull();
        var graph = compileResult.loadClass("TestAppGraph");
        assertThat(graph).isNotNull();
    }

    @Test
    public void testExtensionForNonProcessedType() throws Exception {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                public sealed interface TestInterface {
                  record TestRecord(@Size(min = 1, max = 5) java.util.List<String> list) implements TestInterface {}
                }
                """, """
                @KoraApp
                public interface TestApp extends ValidatorModule{
                   @Root
                   default String root(Validator<TestInterface> testRecordValidator) { return "";}
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestInterface_Validator");
        assertThat(validatorClass).isNotNull();
        var graph = compileResult.loadClass("TestAppGraph");
        assertThat(graph).isNotNull();
    }

}
