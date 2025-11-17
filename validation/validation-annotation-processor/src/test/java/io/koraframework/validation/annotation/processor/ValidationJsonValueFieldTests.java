package io.koraframework.validation.annotation.processor;

import org.junit.jupiter.api.Test;
<<<<<<<< HEAD:validation/validation-annotation-processor/src/test/java/io/koraframework/validation/annotation/processor/ValidationJsonNullableFieldTests.java
import io.koraframework.json.common.JsonNullable;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;
import io.koraframework.validation.common.ValidationContext;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.ValidatorModule;
========
import ru.tinkoff.kora.json.common.JsonValue;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;
import ru.tinkoff.kora.validation.common.ValidationContext;
import ru.tinkoff.kora.validation.common.Validator;
import ru.tinkoff.kora.validation.common.constraint.ValidatorModule;
>>>>>>>> 82ba3753b (JsonNullable refactored to JsonValue & JsonNullable & JsonUndefined contracts for Java):validation/validation-annotation-processor/src/test/java/io/koraframework/validation/annotation/processor/ValidationJsonValueFieldTests.java

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ValidationJsonValueFieldTests extends AbstractValidationAnnotationProcessorTest implements ValidatorModule {

    @Test
    public void fieldJsonNullableIsUndefined() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    
                    @Valid
                    record TestRecord(JsonValue<String> field) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();

        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator");
        var violations = validator.validate(newObject("TestRecord", JsonValue.undefined()));
        assertEquals(0, violations.size());
    }

    @Test
    public void fieldJsonNullableIsNull() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    
                    @Valid
                    record TestRecord(JsonValue<String> field) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();

        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator");
        var violations = validator.validate(newObject("TestRecord", JsonValue.nullValue()));
        assertEquals(0, violations.size());
    }

    @Test
    public void fieldJsonNullableIsPresent() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    
                    @Valid
                    record TestRecord(JsonValue<String> field) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();

        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator");
        var violations = validator.validate(newObject("TestRecord", JsonValue.of("")));
        assertEquals(0, violations.size());
    }


    @Test
    public void fieldJsonNullableGenericIsUndefined() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    
                    @Valid
                    record TestRecord<T>(JsonValue<T> value) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();

        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator");
        var violations = validator.validate(newObject("TestRecord", JsonValue.undefined()));
        assertEquals(0, violations.size());
    }

    @Test
    public void fieldJsonNullableGenericIsNull() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    
                    @Valid
                    record TestRecord<T>(JsonValue<T> value) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();

        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator");
        var violations = validator.validate(newObject("TestRecord", JsonValue.nullValue()));
        assertEquals(0, violations.size());
    }

    @Test
    public void fieldJsonNullableGenericIsPresent() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    
                    @Valid
                    record TestRecord<T>(JsonValue<T> value) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();

        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator");
        var violations = validator.validate(newObject("TestRecord", JsonValue.of("")));
        assertEquals(0, violations.size());
    }

    @Test
    public void fieldJsonNullableNonNullIsUndefined() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    
                    @Valid
                    record TestRecord(@Nonnull JsonValue<String> field) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();

        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator");
        var violations = validator.validate(newObject("TestRecord", JsonValue.undefined()));
        assertEquals(1, violations.size());
    }

    @Test
    public void fieldJsonNullableNonNullIsNull() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    
                    @Valid
                    record TestRecord(@Nonnull JsonValue<String> field) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();

        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator");
        var violations = validator.validate(newObject("TestRecord", JsonValue.nullValue()));
        assertEquals(1, violations.size());
    }

    @Test
    public void fieldJsonNullableNonNullIsPresent() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    
                    @Valid
                    record TestRecord(@Nonnull JsonValue<String> field) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();

        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator");
        var violations = validator.validate(newObject("TestRecord", JsonValue.of("")));
        assertEquals(0, violations.size());
    }

    @Test
    public void fieldJsonNullableWithValidatorIsUndefined() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    
                    @Valid
                    record TestRecord(@NotBlank @NotEmpty JsonValue<String> field) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();
        assertThat(validatorClass.getConstructors()).hasSize(1);

<<<<<<<< HEAD:validation/validation-annotation-processor/src/test/java/io/koraframework/validation/annotation/processor/ValidationJsonNullableFieldTests.java
        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator", notBlankStringValidatorFactory(), notEmptyStringValidatorFactory());
        var violations = validator.validate(newObject("TestRecord", JsonNullable.undefined()));
========
        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory());
        var violations = validator.validate(newObject("TestRecord", JsonValue.undefined()));
>>>>>>>> 82ba3753b (JsonNullable refactored to JsonValue & JsonNullable & JsonUndefined contracts for Java):validation/validation-annotation-processor/src/test/java/io/koraframework/validation/annotation/processor/ValidationJsonValueFieldTests.java
        assertEquals(0, violations.size());
    }

    @Test
    public void fieldJsonNullableWithValidatorIsNull() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    
                    @Valid
                    record TestRecord(@NotBlank @NotEmpty JsonValue<String> field) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();
        assertThat(validatorClass.getConstructors()).hasSize(1);

<<<<<<<< HEAD:validation/validation-annotation-processor/src/test/java/io/koraframework/validation/annotation/processor/ValidationJsonNullableFieldTests.java
        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator", notBlankStringValidatorFactory(), notEmptyStringValidatorFactory());
        var violations = validator.validate(newObject("TestRecord", JsonNullable.nullValue()));
========
        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory());
        var violations = validator.validate(newObject("TestRecord", JsonValue.nullValue()));
>>>>>>>> 82ba3753b (JsonNullable refactored to JsonValue & JsonNullable & JsonUndefined contracts for Java):validation/validation-annotation-processor/src/test/java/io/koraframework/validation/annotation/processor/ValidationJsonValueFieldTests.java
        assertEquals(2, violations.size());
    }

    @Test
    public void fieldJsonNullableWithValidatorIsPresent() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    
                    @Valid
                    record TestRecord(@NotBlank @NotEmpty JsonValue<String> field) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();
        assertThat(validatorClass.getConstructors()).hasSize(1);

<<<<<<<< HEAD:validation/validation-annotation-processor/src/test/java/io/koraframework/validation/annotation/processor/ValidationJsonNullableFieldTests.java
        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator", notBlankStringValidatorFactory(), notEmptyStringValidatorFactory());
        var violations = validator.validate(newObject("TestRecord", JsonNullable.of("1")));
========
        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory());
        var violations = validator.validate(newObject("TestRecord", JsonValue.of("1")));
>>>>>>>> 82ba3753b (JsonNullable refactored to JsonValue & JsonNullable & JsonUndefined contracts for Java):validation/validation-annotation-processor/src/test/java/io/koraframework/validation/annotation/processor/ValidationJsonValueFieldTests.java
        assertEquals(0, violations.size());
    }

    @Test
    public void fieldJsonNullableWithValidatorFailFastIsUndefined() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    
                    @Valid
                    record TestRecord(@NotBlank @NotEmpty JsonValue<String> field) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();
        assertThat(validatorClass.getConstructors()).hasSize(1);

<<<<<<<< HEAD:validation/validation-annotation-processor/src/test/java/io/koraframework/validation/annotation/processor/ValidationJsonNullableFieldTests.java
        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator", notBlankStringValidatorFactory(), notEmptyStringValidatorFactory());
        var violations = validator.validate(newObject("TestRecord", JsonNullable.undefined()), ValidationContext.failFast());
========
        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory());
        var violations = validator.validate(newObject("TestRecord", JsonValue.undefined()), ValidationContext.failFast());
>>>>>>>> 82ba3753b (JsonNullable refactored to JsonValue & JsonNullable & JsonUndefined contracts for Java):validation/validation-annotation-processor/src/test/java/io/koraframework/validation/annotation/processor/ValidationJsonValueFieldTests.java
        assertEquals(0, violations.size());
    }

    @Test
    public void fieldJsonNullableWithValidatorFailFastIsNull() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    
                    @Valid
                    record TestRecord(@NotBlank @NotEmpty JsonValue<String> field) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();
        assertThat(validatorClass.getConstructors()).hasSize(1);

<<<<<<<< HEAD:validation/validation-annotation-processor/src/test/java/io/koraframework/validation/annotation/processor/ValidationJsonNullableFieldTests.java
        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator", notBlankStringValidatorFactory(), notEmptyStringValidatorFactory());
        var violations = validator.validate(newObject("TestRecord", JsonNullable.nullValue()), ValidationContext.failFast());
========
        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory());
        var violations = validator.validate(newObject("TestRecord", JsonValue.nullValue()), ValidationContext.failFast());
>>>>>>>> 82ba3753b (JsonNullable refactored to JsonValue & JsonNullable & JsonUndefined contracts for Java):validation/validation-annotation-processor/src/test/java/io/koraframework/validation/annotation/processor/ValidationJsonValueFieldTests.java
        assertEquals(1, violations.size());
    }

    @Test
    public void fieldJsonNullableWithValidatorFailFastIsPresent() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    @Valid
                    record TestRecord(@NotBlank @NotEmpty JsonValue<String> field) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();
        assertThat(validatorClass.getConstructors()).hasSize(1);

<<<<<<<< HEAD:validation/validation-annotation-processor/src/test/java/io/koraframework/validation/annotation/processor/ValidationJsonNullableFieldTests.java
        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator", notBlankStringValidatorFactory(), notEmptyStringValidatorFactory());
        var violations = validator.validate(newObject("TestRecord", JsonNullable.of("1")), ValidationContext.failFast());
========
        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory());
        var violations = validator.validate(newObject("TestRecord", JsonValue.of("1")), ValidationContext.failFast());
>>>>>>>> 82ba3753b (JsonNullable refactored to JsonValue & JsonNullable & JsonUndefined contracts for Java):validation/validation-annotation-processor/src/test/java/io/koraframework/validation/annotation/processor/ValidationJsonValueFieldTests.java
        assertEquals(0, violations.size());
    }
}
