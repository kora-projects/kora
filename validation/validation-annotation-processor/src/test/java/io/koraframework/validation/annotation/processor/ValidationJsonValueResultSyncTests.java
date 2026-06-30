package io.koraframework.validation.annotation.processor;

import io.koraframework.aop.annotation.processor.AopAnnotationProcessor;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;
import io.koraframework.validation.common.ViolationException;
import io.koraframework.validation.common.constraint.ValidatorModule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class ValidationJsonValueResultSyncTests extends AbstractValidationAnnotationProcessorTest implements ValidatorModule {

    @Test
    public void resultJsonNullableIsUndefined() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    public JsonValue<String> test() {
                        return JsonValue.undefined();
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy");
        assertDoesNotThrow(() -> invoke(component, "test"));
    }

    @Test
    public void resultJsonNullableIsNull() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    public JsonNullable<String> test() {
                        return JsonNullable.nullValue();
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy");
        assertDoesNotThrow(() -> invoke(component, "test"));
    }

    @Test
    public void resultJsonNullableIsPresent() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    public JsonNullable<String> test() {
                        return JsonNullable.of("1");
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy");
        assertDoesNotThrow(() -> invoke(component, "test"));
    }

    @Test
    public void resultJsonNullableNonNullIsUndefined() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    @NonNull
                    public JsonValue<String> test() {
                        return JsonValue.undefined();
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy");
        assertThrows(ViolationException.class, () -> invoke(component, "test"));
    }

    @Test
    public void resultJsonNullableNonNullIsNull() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    @NonNull
                    public JsonNullable<String> test() {
                        return JsonNullable.nullValue();
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy");
        assertThrows(ViolationException.class, () -> invoke(component, "test"));
    }

    @Test
    public void resultJsonNullableNonNullIsPresent() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    @NonNull
                    public JsonNullable<String> test() {
                        return JsonNullable.of("1");
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy");
        assertDoesNotThrow(() -> invoke(component, "test"));
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
                    public JsonValue<String> test() {
                        return JsonValue.undefined();
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy", notBlankStringValidatorFactory(), notEmptyStringValidatorFactory());
        assertDoesNotThrow(() -> invoke(component, "test"));
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
                    public JsonNullable<String> test() {
                        return JsonNullable.nullValue();
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy", notBlankStringValidatorFactory(), notEmptyStringValidatorFactory());
        ViolationException ex = assertThrows(ViolationException.class, () -> invoke(component, "test"));
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
                    public JsonNullable<String> test() {
                        return JsonNullable.of("1");
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy", notBlankStringValidatorFactory(), notEmptyStringValidatorFactory());
        assertDoesNotThrow(() -> invoke(component, "test"));
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
                    public JsonValue<String> test() {
                        return JsonValue.undefined();
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy", notBlankStringValidatorFactory(), notEmptyStringValidatorFactory());
        assertDoesNotThrow(() -> invoke(component, "test"));
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
                    public JsonNullable<String> test() {
                        return JsonNullable.nullValue();
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy", notBlankStringValidatorFactory(), notEmptyStringValidatorFactory());
        ViolationException ex = assertThrows(ViolationException.class, () -> invoke(component, "test"));
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
                    public JsonNullable<String> test() {
                        return JsonNullable.of("1");
                    }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy", notBlankStringValidatorFactory(), notEmptyStringValidatorFactory());
        assertDoesNotThrow(() -> invoke(component, "test"));
    }
}
