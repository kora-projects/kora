package ru.tinkoff.kora.validation.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.aop.annotation.processor.AopAnnotationProcessor;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;
import ru.tinkoff.kora.validation.common.ViolationException;
import ru.tinkoff.kora.validation.common.constraint.ValidatorModule;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class ValidationJsonNullableResultFluxTests extends AbstractValidationAnnotationProcessorTest implements ValidatorModule {

    @Test
    public void resultJsonNullableIsUndefined() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    public Flux<JsonNullable<String>> test() {
                        return Flux.just(JsonNullable.undefined());
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
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    public Flux<JsonNullable<String>> test() {
                        return Flux.just(JsonNullable.nullValue());
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
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    public Flux<JsonNullable<String>> test() {
                        return Flux.just(JsonNullable.of("1"));
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
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    @Nonnull
                    public Flux<JsonNullable<String>> test() {
                        return Flux.just(JsonNullable.undefined());
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
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    @Nonnull
                    public Flux<JsonNullable<String>> test() {
                        return Flux.just(JsonNullable.nullValue());
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
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    @Nonnull
                    public Flux<JsonNullable<String>> test() {
                        return Flux.just(JsonNullable.of("1"));
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
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    @NotBlank
                    @NotEmpty
                    public Flux<JsonNullable<String>> test() {
                        return Flux.just(JsonNullable.undefined());
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
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    @NotBlank
                    @NotEmpty
                    public Flux<JsonNullable<String>> test() {
                        return Flux.just(JsonNullable.nullValue());
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
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    @NotBlank
                    @NotEmpty
                    public Flux<JsonNullable<String>> test() {
                        return Flux.just(JsonNullable.of("1"));
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
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate(failFast = true)
                    @NotBlank
                    @NotEmpty
                    public Flux<JsonNullable<String>> test() {
                        return Flux.just(JsonNullable.undefined());
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
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate(failFast = true)
                    @NotBlank
                    @NotEmpty
                    public Flux<JsonNullable<String>> test() {
                        return Flux.just(JsonNullable.nullValue());
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
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate(failFast = true)
                    @NotBlank
                    @NotEmpty
                    public Flux<JsonNullable<String>> test() {
                        return Flux.just(JsonNullable.of("1"));
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
