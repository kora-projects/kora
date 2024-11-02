package ru.tinkoff.kora.validation.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.json.common.JsonNullable;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;
import ru.tinkoff.kora.validation.common.ValidationContext;
import ru.tinkoff.kora.validation.common.Validator;
import ru.tinkoff.kora.validation.common.constraint.ValidatorModule;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ValidationJsonNullableFieldTests extends AbstractValidationAnnotationProcessorTest implements ValidatorModule {

    @Test
    public void fieldJsonNullableIsUndefined() {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    import ru.tinkoff.kora.json.common.JsonNullable;
                    @Valid
                    record TestRecord(JsonNullable<String> value) {}
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
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    import ru.tinkoff.kora.json.common.JsonNullable;
                    @Valid
                    record TestRecord(JsonNullable<String> value) {}
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
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    import ru.tinkoff.kora.json.common.JsonNullable;
                    @Valid
                    record TestRecord(JsonNullable<String> value) {}
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
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    import ru.tinkoff.kora.json.common.JsonNullable;
                    @Valid
                    record TestRecord(@Nonnull JsonNullable<String> value) {}
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
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    import ru.tinkoff.kora.json.common.JsonNullable;
                    @Valid
                    record TestRecord(@Nonnull JsonNullable<String> value) {}
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
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    import ru.tinkoff.kora.json.common.JsonNullable;
                    @Valid
                    record TestRecord(@Nonnull JsonNullable<String> value) {}
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
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    import ru.tinkoff.kora.json.common.JsonNullable;
                    @Valid
                    record TestRecord(@NotBlank @NotEmpty JsonNullable<String> value) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();
        assertThat(validatorClass.getConstructors()).hasSize(1);

        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator", notEmptyStringConstraintFactory(), notBlankStringConstraintFactory());
        var violations = validator.validate(newObject("TestRecord", JsonNullable.undefined()));
        assertEquals(0, violations.size());
    }

    @Test
    public void fieldJsonNullableWithValidatorIsNull() {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    import ru.tinkoff.kora.json.common.JsonNullable;
                    @Valid
                    record TestRecord(@NotBlank @NotEmpty JsonNullable<String> value) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();
        assertThat(validatorClass.getConstructors()).hasSize(1);

        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator", notEmptyStringConstraintFactory(), notBlankStringConstraintFactory());
        var violations = validator.validate(newObject("TestRecord", JsonNullable.nullValue()));
        assertEquals(2, violations.size());
    }

    @Test
    public void fieldJsonNullableWithValidatorIsPresent() {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    import ru.tinkoff.kora.json.common.JsonNullable;
                    @Valid
                    record TestRecord(@NotBlank @NotEmpty JsonNullable<String> value) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();
        assertThat(validatorClass.getConstructors()).hasSize(1);

        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator", notEmptyStringConstraintFactory(), notBlankStringConstraintFactory());
        var violations = validator.validate(newObject("TestRecord", JsonNullable.of("1")));
        assertEquals(0, violations.size());
    }

    @Test
    public void fieldJsonNullableWithValidatorFailFastIsUndefined() {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    import ru.tinkoff.kora.json.common.JsonNullable;
                    @Valid
                    record TestRecord(@NotBlank @NotEmpty JsonNullable<String> value) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();
        assertThat(validatorClass.getConstructors()).hasSize(1);

        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator", notEmptyStringConstraintFactory(), notBlankStringConstraintFactory());
        var violations = validator.validate(newObject("TestRecord", JsonNullable.undefined()), ValidationContext.failFast());
        assertEquals(0, violations.size());
    }

    @Test
    public void fieldJsonNullableWithValidatorFailFastIsNull() {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    import ru.tinkoff.kora.json.common.JsonNullable;
                    @Valid
                    record TestRecord(@NotBlank @NotEmpty JsonNullable<String> value) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();
        assertThat(validatorClass.getConstructors()).hasSize(1);

        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator", notEmptyStringConstraintFactory(), notBlankStringConstraintFactory());
        var violations = validator.validate(newObject("TestRecord", JsonNullable.nullValue()), ValidationContext.failFast());
        assertEquals(1, violations.size());
    }

    @Test
    public void fieldJsonNullableWithValidatorFailFastIsPresent() {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    import ru.tinkoff.kora.json.common.JsonNullable;
                    @Valid
                    record TestRecord(@NotBlank @NotEmpty JsonNullable<String> value) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();
        assertThat(validatorClass.getConstructors()).hasSize(1);

        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator", notEmptyStringConstraintFactory(), notBlankStringConstraintFactory());
        var violations = validator.validate(newObject("TestRecord", JsonNullable.of("1")), ValidationContext.failFast());
        assertEquals(0, violations.size());
    }
}
