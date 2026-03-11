package io.koraframework.validation.annotation.processor;

import org.junit.jupiter.api.Test;
import io.koraframework.json.common.JsonNullable;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;
import io.koraframework.validation.common.ValidationContext;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.ValidatorModule;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ValidationJsonNullableFieldTests extends AbstractValidationAnnotationProcessorTest implements ValidatorModule {

    @Test
    public void fieldJsonNullableIsUndefined() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    
                    @Valid
                    record TestRecord(JsonNullable<String> field) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();

        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator");
        var violations = validator.validate(newObject("TestRecord", JsonNullable.undefined()));
        assertEquals(0, violations.size());
    }

    @Test
    public void fieldJsonNullableIsNull() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    
                    @Valid
                    record TestRecord(JsonNullable<String> field) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();

        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator");
        var violations = validator.validate(newObject("TestRecord", JsonNullable.nullValue()));
        assertEquals(0, violations.size());
    }

    @Test
    public void fieldJsonNullableIsPresent() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    
                    @Valid
                    record TestRecord(JsonNullable<String> field) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();

        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator");
        var violations = validator.validate(newObject("TestRecord", JsonNullable.of("")));
        assertEquals(0, violations.size());
    }


    @Test
    public void fieldJsonNullableGenericIsUndefined() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    
                    @Valid
                    record TestRecord<T>(JsonNullable<T> value) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();

        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator");
        var violations = validator.validate(newObject("TestRecord", JsonNullable.undefined()));
        assertEquals(0, violations.size());
    }

    @Test
    public void fieldJsonNullableGenericIsNull() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    
                    @Valid
                    record TestRecord<T>(JsonNullable<T> value) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();

        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator");
        var violations = validator.validate(newObject("TestRecord", JsonNullable.nullValue()));
        assertEquals(0, violations.size());
    }

    @Test
    public void fieldJsonNullableGenericIsPresent() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    
                    @Valid
                    record TestRecord<T>(JsonNullable<T> value) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();

        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator");
        var violations = validator.validate(newObject("TestRecord", JsonNullable.of("")));
        assertEquals(0, violations.size());
    }

    @Test
    public void fieldJsonNullableNonNullIsUndefined() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    
                    @Valid
                    record TestRecord(@NonNull JsonNullable<String> field) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();

        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator");
        var violations = validator.validate(newObject("TestRecord", JsonNullable.undefined()));
        assertEquals(1, violations.size());
    }

    @Test
    public void fieldJsonNullableNonNullIsNull() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    
                    @Valid
                    record TestRecord(@NonNull JsonNullable<String> field) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();

        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator");
        var violations = validator.validate(newObject("TestRecord", JsonNullable.nullValue()));
        assertEquals(1, violations.size());
    }

    @Test
    public void fieldJsonNullableNonNullIsPresent() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    
                    @Valid
                    record TestRecord(@NonNull JsonNullable<String> field) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();

        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator");
        var violations = validator.validate(newObject("TestRecord", JsonNullable.of("")));
        assertEquals(0, violations.size());
    }

    @Test
    public void fieldJsonNullableWithValidatorIsUndefined() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    
                    @Valid
                    record TestRecord(@NotBlank @NotEmpty JsonNullable<String> field) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();
        assertThat(validatorClass.getConstructors()).hasSize(1);

        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory());
        var violations = validator.validate(newObject("TestRecord", JsonNullable.undefined()));
        assertEquals(0, violations.size());
    }

    @Test
    public void fieldJsonNullableWithValidatorIsNull() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    
                    @Valid
                    record TestRecord(@NotBlank @NotEmpty JsonNullable<String> field) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();
        assertThat(validatorClass.getConstructors()).hasSize(1);

        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory());
        var violations = validator.validate(newObject("TestRecord", JsonNullable.nullValue()));
        assertEquals(2, violations.size());
    }

    @Test
    public void fieldJsonNullableWithValidatorIsPresent() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    
                    @Valid
                    record TestRecord(@NotBlank @NotEmpty JsonNullable<String> field) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();
        assertThat(validatorClass.getConstructors()).hasSize(1);

        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory());
        var violations = validator.validate(newObject("TestRecord", JsonNullable.of("1")));
        assertEquals(0, violations.size());
    }

    @Test
    public void fieldJsonNullableWithValidatorFailFastIsUndefined() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    
                    @Valid
                    record TestRecord(@NotBlank @NotEmpty JsonNullable<String> field) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();
        assertThat(validatorClass.getConstructors()).hasSize(1);

        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory());
        var violations = validator.validate(newObject("TestRecord", JsonNullable.undefined()), ValidationContext.failFast());
        assertEquals(0, violations.size());
    }

    @Test
    public void fieldJsonNullableWithValidatorFailFastIsNull() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    
                    @Valid
                    record TestRecord(@NotBlank @NotEmpty JsonNullable<String> field) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();
        assertThat(validatorClass.getConstructors()).hasSize(1);

        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory());
        var violations = validator.validate(newObject("TestRecord", JsonNullable.nullValue()), ValidationContext.failFast());
        assertEquals(1, violations.size());
    }

    @Test
    public void fieldJsonNullableWithValidatorFailFastIsPresent() {
        var compileResult = compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    @Valid
                    record TestRecord(@NotBlank @NotEmpty JsonNullable<String> field) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();
        assertThat(validatorClass.getConstructors()).hasSize(1);

        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory());
        var violations = validator.validate(newObject("TestRecord", JsonNullable.of("1")), ValidationContext.failFast());
        assertEquals(0, violations.size());
    }
}
