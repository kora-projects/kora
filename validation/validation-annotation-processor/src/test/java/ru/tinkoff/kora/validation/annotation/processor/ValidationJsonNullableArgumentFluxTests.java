package ru.tinkoff.kora.validation.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.aop.annotation.processor.AopAnnotationProcessor;
import ru.tinkoff.kora.json.common.JsonNullable;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;
import ru.tinkoff.kora.validation.common.ViolationException;
import ru.tinkoff.kora.validation.common.constraint.ValidatorModule;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class ValidationJsonNullableArgumentFluxTests extends AbstractValidationAnnotationProcessorTest implements ValidatorModule {

    @Test
    public void argumentJsonNullableIsUndefined() {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    public Flux<Void> test(JsonNullable<String> arg) {
                        return Flux.empty();
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy");
        assertDoesNotThrow(() -> invokeAndCast(component, "test", JsonNullable.undefined()));
    }

    @Test
    public void argumentJsonNullableIsNull() {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    public Flux<Void> test(JsonNullable<String> arg) {
                        return Flux.empty();
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy");
        assertDoesNotThrow(() -> invokeAndCast(component, "test", JsonNullable.nullValue()));
    }

    @Test
    public void argumentJsonNullableIsPresent() {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    public Flux<Void> test(JsonNullable<String> arg) {
                        return Flux.empty();
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy");
        assertDoesNotThrow(() -> invokeAndCast(component, "test", JsonNullable.of("1")));
    }

    @Test
    public void argumentJsonNullableNonNullIsUndefined() {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    public Flux<Void> test(@Nonnull JsonNullable<String> arg) {
                        return Flux.empty();
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy");
        assertThrows(ViolationException.class, () -> invokeAndCast(component, "test", JsonNullable.undefined()));
    }

    @Test
    public void argumentJsonNullableNonNullIsNull() {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    public Flux<Void> test(@Nonnull JsonNullable<String> arg) {
                        return Flux.empty();
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy");
        assertThrows(ViolationException.class, () -> invokeAndCast(component, "test", JsonNullable.nullValue()));
    }

    @Test
    public void argumentJsonNullableNonNullIsPresent() {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    public Flux<Void> test(@Nonnull JsonNullable<String> arg) {
                        return Flux.empty();
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy");
        assertDoesNotThrow(() -> invokeAndCast(component, "test", JsonNullable.of("1")));
    }

    @Test
    public void argumentJsonNullableWithValidatorIsUndefined() {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    public Flux<Void> test(@NotBlank @NotEmpty JsonNullable<String> arg) {
                        return Flux.empty();
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory());
        assertDoesNotThrow(() -> invokeAndCast(component, "test", JsonNullable.undefined()));
    }

    @Test
    public void argumentJsonNullableWithValidatorIsNull() {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    public Flux<Void> test(@NotBlank @NotEmpty JsonNullable<String> arg) {
                        return Flux.empty();
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory());
        ViolationException ex = assertThrows(ViolationException.class, () -> invokeAndCast(component, "test", JsonNullable.nullValue()));
        assertEquals(2, ex.getViolations().size());
    }

    @Test
    public void argumentJsonNullableWithValidatorIsPresent() {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    public Flux<Void> test(@NotBlank @NotEmpty JsonNullable<String> arg) {
                        return Flux.empty();
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory());
        assertDoesNotThrow(() -> invokeAndCast(component, "test", JsonNullable.of("1")));
    }

    @Test
    public void argumentJsonNullableWithValidatorFailFastIsUndefined() {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate(failFast = true)
                    public Flux<Void> test(@NotBlank @NotEmpty JsonNullable<String> arg) {
                        return Flux.empty();
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory());
        assertDoesNotThrow(() -> invokeAndCast(component, "test", JsonNullable.undefined()));
    }

    @Test
    public void argumentJsonNullableWithValidatorFailFastIsNull() {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate(failFast = true)
                    public Flux<Void> test(@NotBlank @NotEmpty JsonNullable<String> arg) {
                        return Flux.empty();
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory());
        ViolationException ex = assertThrows(ViolationException.class, () -> invokeAndCast(component, "test", JsonNullable.nullValue()));
        assertEquals(1, ex.getViolations().size());
    }

    @Test
    public void argumentJsonNullableWithValidatorFailFastIsPresent() {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate(failFast = true)
                    public Flux<Void> test(@NotBlank @NotEmpty JsonNullable<String> arg) {
                        return Flux.empty();
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory());
        assertDoesNotThrow(() -> invokeAndCast(component, "test", JsonNullable.of("1")));
    }
}
