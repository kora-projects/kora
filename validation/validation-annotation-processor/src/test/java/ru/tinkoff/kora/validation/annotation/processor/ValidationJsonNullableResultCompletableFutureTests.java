package ru.tinkoff.kora.validation.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.aop.annotation.processor.AopAnnotationProcessor;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;
import ru.tinkoff.kora.validation.common.ViolationException;
import ru.tinkoff.kora.validation.common.constraint.ValidatorModule;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class ValidationJsonNullableResultCompletableFutureTests extends AbstractValidationAnnotationProcessorTest implements ValidatorModule {

    @Test
    public void resultJsonNullableIsUndefined() {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    public CompletableFuture<JsonNullable<String>> test() {
                        return CompletableFuture.completedFuture(JsonNullable.undefined());
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy");
        assertDoesNotThrow(() -> invokeAndCast(component, "test"));
    }

    @Test
    public void resultJsonNullableIsNull() {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    public CompletableFuture<JsonNullable<String>> test() {
                        return CompletableFuture.completedFuture(JsonNullable.nullValue());
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy");
        assertDoesNotThrow(() -> invokeAndCast(component, "test"));
    }

    @Test
    public void resultJsonNullableIsPresent() {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    public CompletableFuture<JsonNullable<String>> test() {
                        return CompletableFuture.completedFuture(JsonNullable.of("1"));
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy");
        assertDoesNotThrow(() -> invokeAndCast(component, "test"));
    }

    @Test
    public void resultJsonNullableNonNullIsUndefined() {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    @Nonnull
                    public CompletableFuture<JsonNullable<String>> test() {
                        return CompletableFuture.completedFuture(JsonNullable.undefined());
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy");
        assertThrows(ViolationException.class, () -> invokeAndCast(component, "test"));
    }

    @Test
    public void resultJsonNullableNonNullIsNull() {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    @Nonnull
                    public CompletableFuture<JsonNullable<String>> test() {
                        return CompletableFuture.completedFuture(JsonNullable.nullValue());
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy");
        assertThrows(ViolationException.class, () -> invokeAndCast(component, "test"));
    }

    @Test
    public void resultJsonNullableNonNullIsPresent() {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    @Nonnull
                    public CompletableFuture<JsonNullable<String>> test() {
                        return CompletableFuture.completedFuture(JsonNullable.of("1"));
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy");
        assertDoesNotThrow(() -> invokeAndCast(component, "test"));
    }

    @Test
    public void resultJsonNullableWithValidatorIsUndefined() {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    @NotBlank
                    @NotEmpty
                    public CompletableFuture<JsonNullable<String>> test() {
                        return CompletableFuture.completedFuture(JsonNullable.undefined());
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory());
        assertDoesNotThrow(() -> invokeAndCast(component, "test"));
    }

    @Test
    public void resultJsonNullableWithValidatorIsNull() {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    @NotBlank
                    @NotEmpty
                    public CompletableFuture<JsonNullable<String>> test() {
                        return CompletableFuture.completedFuture(JsonNullable.nullValue());
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory());
        ViolationException ex = assertThrows(ViolationException.class, () -> invokeAndCast(component, "test"));
        assertEquals(2, ex.getViolations().size());
    }

    @Test
    public void resultJsonNullableWithValidatorIsPresent() {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    @NotBlank
                    @NotEmpty
                    public CompletableFuture<JsonNullable<String>> test() {
                        return CompletableFuture.completedFuture(JsonNullable.of("1"));
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory());
        assertDoesNotThrow(() -> invokeAndCast(component, "test"));
    }

    @Test
    public void resultJsonNullableWithValidatorFailFastIsUndefined() {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate(failFast = true)
                    @NotBlank
                    @NotEmpty
                    public CompletableFuture<JsonNullable<String>> test() {
                        return CompletableFuture.completedFuture(JsonNullable.undefined());
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory());
        assertDoesNotThrow(() -> invokeAndCast(component, "test"));
    }

    @Test
    public void resultJsonNullableWithValidatorFailFastIsNull() {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate(failFast = true)
                    @NotBlank
                    @NotEmpty
                    public CompletableFuture<JsonNullable<String>> test() {
                        return CompletableFuture.completedFuture(JsonNullable.nullValue());
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory());
        ViolationException ex = assertThrows(ViolationException.class, () -> invokeAndCast(component, "test"));
        assertEquals(1, ex.getViolations().size());
    }

    @Test
    public void resultJsonNullableWithValidatorFailFastIsPresent() {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate(failFast = true)
                    @NotBlank
                    @NotEmpty
                    public CompletableFuture<JsonNullable<String>> test() {
                        return CompletableFuture.completedFuture(JsonNullable.of("1"));
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory());
        assertDoesNotThrow(() -> invokeAndCast(component, "test"));
    }
}
