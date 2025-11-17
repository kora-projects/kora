package io.koraframework.validation.annotation.processor;

import org.junit.jupiter.api.Test;
<<<<<<<< HEAD:validation/validation-annotation-processor/src/test/java/io/koraframework/validation/annotation/processor/ValidationJsonNullableArgumentSyncTests.java
import io.koraframework.aop.annotation.processor.AopAnnotationProcessor;
import io.koraframework.json.common.JsonNullable;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;
import io.koraframework.validation.common.ViolationException;
import io.koraframework.validation.common.constraint.ValidatorModule;
========
import ru.tinkoff.kora.aop.annotation.processor.AopAnnotationProcessor;
import ru.tinkoff.kora.json.common.JsonValue;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;
import ru.tinkoff.kora.validation.common.ViolationException;
import ru.tinkoff.kora.validation.common.constraint.ValidatorModule;
>>>>>>>> 82ba3753b (JsonNullable refactored to JsonValue & JsonNullable & JsonUndefined contracts for Java):validation/validation-annotation-processor/src/test/java/io/koraframework/validation/annotation/processor/ValidationJsonValueArgumentSyncTests.java

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class ValidationJsonValueArgumentSyncTests extends AbstractValidationAnnotationProcessorTest implements ValidatorModule {

    @Test
    public void argumentJsonNullableIsUndefined() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    public void test(JsonValue<String> arg) { }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy");
        assertDoesNotThrow(() -> invoke(component, "test", JsonValue.undefined()));
    }

    @Test
    public void argumentJsonNullableIsNull() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    public void test(JsonValue<String> arg) { }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy");
        assertDoesNotThrow(() -> invoke(component, "test", JsonValue.nullValue()));
    }

    @Test
    public void argumentJsonNullableIsPresent() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    public void test(JsonValue<String> arg) { }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy");
        assertDoesNotThrow(() -> invoke(component, "test", JsonValue.of("1")));
    }

    @Test
    public void argumentJsonNullableNonNullIsUndefined() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    public void test(@Nonnull JsonValue<String> arg) { }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy");
        assertThrows(ViolationException.class, () -> invoke(component, "test", JsonValue.undefined()));
    }

    @Test
    public void argumentJsonNullableNonNullIsNull() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    public void test(@Nonnull JsonValue<String> arg) { }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy");
        assertThrows(ViolationException.class, () -> invoke(component, "test", JsonValue.nullValue()));
    }

    @Test
    public void argumentJsonNullableNonNullIsPresent() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    public void test(@Nonnull JsonValue<String> arg) { }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

        var component = newObject("$TestComponent__AopProxy");
        assertDoesNotThrow(() -> invoke(component, "test", JsonValue.of("1")));
    }

    @Test
    public void argumentJsonNullableWithValidatorIsUndefined() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    public void test(@NotBlank @NotEmpty JsonValue<String> arg) { }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

<<<<<<<< HEAD:validation/validation-annotation-processor/src/test/java/io/koraframework/validation/annotation/processor/ValidationJsonNullableArgumentSyncTests.java
        var component = newObject("$TestComponent__AopProxy", notBlankStringValidatorFactory(), notEmptyStringValidatorFactory());
        assertDoesNotThrow(() -> invoke(component, "test", JsonNullable.undefined()));
========
        var component = newObject("$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory());
        assertDoesNotThrow(() -> invoke(component, "test", JsonValue.undefined()));
>>>>>>>> 82ba3753b (JsonNullable refactored to JsonValue & JsonNullable & JsonUndefined contracts for Java):validation/validation-annotation-processor/src/test/java/io/koraframework/validation/annotation/processor/ValidationJsonValueArgumentSyncTests.java
    }

    @Test
    public void argumentJsonNullableWithValidatorIsNull() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    public void test(@NotBlank @NotEmpty JsonValue<String> arg) { }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

<<<<<<<< HEAD:validation/validation-annotation-processor/src/test/java/io/koraframework/validation/annotation/processor/ValidationJsonNullableArgumentSyncTests.java
        var component = newObject("$TestComponent__AopProxy", notBlankStringValidatorFactory(), notEmptyStringValidatorFactory());
        ViolationException ex = assertThrows(ViolationException.class, () -> invoke(component, "test", JsonNullable.nullValue()));
========
        var component = newObject("$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory());
        ViolationException ex = assertThrows(ViolationException.class, () -> invoke(component, "test", JsonValue.nullValue()));
>>>>>>>> 82ba3753b (JsonNullable refactored to JsonValue & JsonNullable & JsonUndefined contracts for Java):validation/validation-annotation-processor/src/test/java/io/koraframework/validation/annotation/processor/ValidationJsonValueArgumentSyncTests.java
        assertEquals(2, ex.getViolations().size());
    }

    @Test
    public void argumentJsonNullableWithValidatorIsPresent() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate
                    public void test(@NotBlank @NotEmpty JsonValue<String> arg) { }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

<<<<<<<< HEAD:validation/validation-annotation-processor/src/test/java/io/koraframework/validation/annotation/processor/ValidationJsonNullableArgumentSyncTests.java
        var component = newObject("$TestComponent__AopProxy", notBlankStringValidatorFactory(), notEmptyStringValidatorFactory());
        assertDoesNotThrow(() -> invoke(component, "test", JsonNullable.of("1")));
========
        var component = newObject("$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory());
        assertDoesNotThrow(() -> invoke(component, "test", JsonValue.of("1")));
>>>>>>>> 82ba3753b (JsonNullable refactored to JsonValue & JsonNullable & JsonUndefined contracts for Java):validation/validation-annotation-processor/src/test/java/io/koraframework/validation/annotation/processor/ValidationJsonValueArgumentSyncTests.java
    }

    @Test
    public void argumentJsonNullableWithValidatorFailFastIsUndefined() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate(failFast = true)
                    public void test(@NotBlank @NotEmpty JsonValue<String> arg) { }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

<<<<<<<< HEAD:validation/validation-annotation-processor/src/test/java/io/koraframework/validation/annotation/processor/ValidationJsonNullableArgumentSyncTests.java
        var component = newObject("$TestComponent__AopProxy", notBlankStringValidatorFactory(), notEmptyStringValidatorFactory());
        assertDoesNotThrow(() -> invoke(component, "test", JsonNullable.undefined()));
========
        var component = newObject("$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory());
        assertDoesNotThrow(() -> invoke(component, "test", JsonValue.undefined()));
>>>>>>>> 82ba3753b (JsonNullable refactored to JsonValue & JsonNullable & JsonUndefined contracts for Java):validation/validation-annotation-processor/src/test/java/io/koraframework/validation/annotation/processor/ValidationJsonValueArgumentSyncTests.java
    }

    @Test
    public void argumentJsonNullableWithValidatorFailFastIsNull() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate(failFast = true)
                    public void test(@NotBlank @NotEmpty JsonValue<String> arg) { }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

<<<<<<<< HEAD:validation/validation-annotation-processor/src/test/java/io/koraframework/validation/annotation/processor/ValidationJsonNullableArgumentSyncTests.java
        var component = newObject("$TestComponent__AopProxy", notBlankStringValidatorFactory(), notEmptyStringValidatorFactory());
        ViolationException ex = assertThrows(ViolationException.class, () -> invoke(component, "test", JsonNullable.nullValue()));
========
        var component = newObject("$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory());
        ViolationException ex = assertThrows(ViolationException.class, () -> invoke(component, "test", JsonValue.nullValue()));
>>>>>>>> 82ba3753b (JsonNullable refactored to JsonValue & JsonNullable & JsonUndefined contracts for Java):validation/validation-annotation-processor/src/test/java/io/koraframework/validation/annotation/processor/ValidationJsonValueArgumentSyncTests.java
        assertEquals(1, ex.getViolations().size());
    }

    @Test
    public void argumentJsonNullableWithValidatorFailFastIsPresent() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()),
            """
                @Component
                public class TestComponent {
                    @Validate(failFast = true)
                    public void test(@NotBlank @NotEmpty JsonValue<String> arg) { }
                }
                """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestComponent__AopProxy");
        assertThat(validatorClass).isNotNull();

<<<<<<<< HEAD:validation/validation-annotation-processor/src/test/java/io/koraframework/validation/annotation/processor/ValidationJsonNullableArgumentSyncTests.java
        var component = newObject("$TestComponent__AopProxy", notBlankStringValidatorFactory(), notEmptyStringValidatorFactory());
        assertDoesNotThrow(() -> invoke(component, "test", JsonNullable.of("1")));
========
        var component = newObject("$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory());
        assertDoesNotThrow(() -> invoke(component, "test", JsonValue.of("1")));
>>>>>>>> 82ba3753b (JsonNullable refactored to JsonValue & JsonNullable & JsonUndefined contracts for Java):validation/validation-annotation-processor/src/test/java/io/koraframework/validation/annotation/processor/ValidationJsonValueArgumentSyncTests.java
    }
}
