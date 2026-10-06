package io.koraframework.validation.annotation.processor;

import io.koraframework.aop.annotation.processor.AopAnnotationProcessor;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.ViolationException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Custom {@code @ValidatedBy} constraints: the factory `create` arguments must be literals of the declared annotation member types.
 */
public class ValidationCustomConstraintTests extends AbstractValidationAnnotationProcessorTest {

    private static final String MODE = """
        public enum Mode { A, B }
        """;

    private static final String ANNOTATION = """
        @ValidatedBy(AllowedFactory.class)
        @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS)
        public @interface Allowed {
            int[] codes();
            Mode[] modes();
            Class<?>[] types();
            String[] names();
        }
        """;

    private static final String FACTORY = """
        public class AllowedFactory implements io.koraframework.validation.common.ValidatorFactory<Integer> {
            @Override
            public Validator<Integer> create() {
                throw new UnsupportedOperationException();
            }

            public Validator<Integer> create(int[] codes, Mode[] modes, Class<?>[] types, String[] names) {
                var attributesOk = modes.length == 2 && modes[1] == Mode.B
                    && types.length == 1 && types[0] == String.class
                    && names.length == 1 && names[0].equals("x");
                return (value, context) -> attributesOk && java.util.Arrays.stream(codes).anyMatch(c -> c == value)
                    ? java.util.List.of()
                    : java.util.List.of(context.violates("not allowed"));
            }
        }
        """;

    @Test
    public void customConstraintWithNonStringArrayAttributesOnField() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            MODE, ANNOTATION, FACTORY, """
                @Valid
                public record TestRecord(@Allowed(codes = {200, 201}, modes = {Mode.A, Mode.B}, types = String.class, names = "x") Integer code) {}
                """);
        compileResult.assertSuccess();

        @SuppressWarnings("unchecked")
        var validator = (Validator<Object>) newObject("$TestRecord_Validator", newObject("AllowedFactory"));
        assertEquals(0, validator.validate(newObject("TestRecord", 201)).size());
        assertEquals(1, validator.validate(newObject("TestRecord", 500)).size());
        assertNoRawClassArray("$TestRecord_Validator");
    }

    @Test
    public void customConstraintWithNonStringArrayAttributesOnValidateArgument() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            MODE, ANNOTATION, FACTORY, """
                @Component
                public class TestComponent {
                    @Validate
                    public void test(@Allowed(codes = {200, 201}, modes = {Mode.A, Mode.B}, types = String.class, names = "x") Integer code) { }
                }
                """);
        compileResult.assertSuccess();

        var component = newObject("$TestComponent__AopProxy", newObject("AllowedFactory"));
        assertDoesNotThrow(() -> invoke(component, "test", 200));
        assertThrows(ViolationException.class, () -> invoke(component, "test", 500));
        assertNoRawClassArray("$TestComponent__AopProxy");
    }

    // a raw `new java.lang.Class[]` fails user builds with -Xlint:all -Werror ([rawtypes])
    private void assertNoRawClassArray(String generatedClass) {
        try {
            var source = Files.readString(Path.of("build/in-test-generated/sources", testPackage().replace('.', '/'), generatedClass + ".java"));
            assertTrue(source.contains("new java.lang.Class<?>[] {java.lang.String.class}"), source);
            assertFalse(source.contains("new java.lang.Class[]"), source);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
