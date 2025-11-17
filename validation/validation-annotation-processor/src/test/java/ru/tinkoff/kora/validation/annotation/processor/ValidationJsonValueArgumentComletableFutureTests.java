package ru.tinkoff.kora.validation.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.aop.annotation.processor.AopAnnotationProcessor;
import ru.tinkoff.kora.json.common.JsonValue;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;
import ru.tinkoff.kora.validation.common.ViolationException;
import ru.tinkoff.kora.validation.common.constraint.ValidatorModule;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class ValidationJsonValueArgumentComletableFutureTests extends AbstractValidationAnnotationProcessorTest implements ValidatorModule {

    @Test
    public void argumentJsonNullableIsUndefined() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    public CompletableFuture<Void> test(JsonValue<String> arg) {
                        return CompletableFuture.completedFuture(null);
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy");
        assertDoesNotThrow(() -> invokeAndCast(component, "test", JsonValue.undefined()));
    }

    @Test
    public void argumentJsonNullableIsNull() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    public CompletableFuture<Void> test(JsonValue<String> arg) {
                        return CompletableFuture.completedFuture(null);
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy");
        assertDoesNotThrow(() -> invokeAndCast(component, "test", JsonValue.nullValue()));
    }

    @Test
    public void argumentJsonNullableIsPresent() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    public CompletableFuture<Void> test(JsonValue<String> arg) {
                        return CompletableFuture.completedFuture(null);
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy");
        assertDoesNotThrow(() -> invokeAndCast(component, "test", JsonValue.of("1")));
    }

    @Test
    public void argumentJsonNullableNonNullIsUndefined() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    public CompletableFuture<Void> test(@Nonnull JsonValue<String> arg) {
                        return CompletableFuture.completedFuture(null);
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy");
        assertThrows(ViolationException.class, () -> invokeAndCast(component, "test", JsonValue.undefined()));
    }

    @Test
    public void argumentJsonNullableNonNullIsNull() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    public CompletableFuture<Void> test(@Nonnull JsonValue<String> arg) {
                        return CompletableFuture.completedFuture(null);
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy");
        assertThrows(ViolationException.class, () -> invokeAndCast(component, "test", JsonValue.nullValue()));
    }

    @Test
    public void argumentJsonNullableNonNullIsPresent() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    public CompletableFuture<Void> test(@Nonnull JsonValue<String> arg) {
                        return CompletableFuture.completedFuture(null);
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy");
        assertDoesNotThrow(() -> invokeAndCast(component, "test", JsonValue.of("1")));
    }

    @Test
    public void argumentJsonNullableWithValidatorIsUndefined() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    public CompletableFuture<Void> test(@NotBlank @NotEmpty JsonValue<String> arg) {
                        return CompletableFuture.completedFuture(null);
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory());
        assertDoesNotThrow(() -> invokeAndCast(component, "test", JsonValue.undefined()));
    }

    @Test
    public void argumentJsonNullableWithValidatorIsNull() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    public CompletableFuture<Void> test(@NotBlank @NotEmpty JsonValue<String> arg) {
                        return CompletableFuture.completedFuture(null);
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory());
        ViolationException ex = assertThrows(ViolationException.class, () -> invokeAndCast(component, "test", JsonValue.nullValue()));
        assertEquals(2, ex.getViolations().size());
    }

    @Test
    public void argumentJsonNullableWithValidatorIsPresent() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    public CompletableFuture<Void> test(@NotBlank @NotEmpty JsonValue<String> arg) {
                        return CompletableFuture.completedFuture(null);
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory());
        assertDoesNotThrow(() -> invokeAndCast(component, "test", JsonValue.of("1")));
    }

    @Test
    public void argumentJsonNullableWithValidatorFailFastIsUndefined() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate(failFast = true)
                    public CompletableFuture<Void> test(@NotBlank @NotEmpty JsonValue<String> arg) {
                        return CompletableFuture.completedFuture(null);
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory());
        assertDoesNotThrow(() -> invokeAndCast(component, "test", JsonValue.undefined()));
    }

    @Test
    public void argumentJsonNullableWithValidatorFailFastIsNull() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate(failFast = true)
                    public CompletableFuture<Void> test(@NotBlank @NotEmpty JsonValue<String> arg) {
                        return CompletableFuture.completedFuture(null);
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory());
        ViolationException ex = assertThrows(ViolationException.class, () -> invokeAndCast(component, "test", JsonValue.nullValue()));
        assertEquals(1, ex.getViolations().size());
    }

    @Test
    public void argumentJsonNullableWithValidatorFailFastIsPresent() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate(failFast = true)
                    public CompletableFuture<Void> test(@NotBlank @NotEmpty JsonValue<String> arg) {
                        return CompletableFuture.completedFuture(null);
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory());
        assertDoesNotThrow(() -> invokeAndCast(component, "test", JsonValue.of("1")));
    }
}
