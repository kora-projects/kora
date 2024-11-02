package ru.tinkoff.kora.validation.annotation.processor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;
import ru.tinkoff.kora.json.common.JsonNullable;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;
import ru.tinkoff.kora.validation.common.ValidationContext;
import ru.tinkoff.kora.validation.common.Validator;
import ru.tinkoff.kora.validation.common.Violation;
import ru.tinkoff.kora.validation.common.constraint.ValidatorModule;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ValidationJsonNullableTests extends AbstractValidationAnnotationProcessorTest implements ValidatorModule {

    @Test
    public void jsonNullableIsUndefined() {
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
    public void jsonNullableIsNull() {
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
    public void jsonNullableIsPresent() {
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
    public void jsonNullableNonNullIsUndefined() {
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
    public void jsonNullableNonNullIsNull() {
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
    public void jsonNullableNonNullIsPresent() {
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
    public void jsonNullableWithValidatorIsUndefined() {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    import ru.tinkoff.kora.json.common.JsonNullable;
                    @Valid
                    record TestRecord(@NotEmpty JsonNullable<String> value) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();
        assertThat(validatorClass.getConstructors()).hasSize(1);

        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator", notEmptyStringConstraintFactory());
        var violations = validator.validate(newObject("TestRecord", JsonNullable.undefined()));
        assertEquals(0, violations.size());
    }

    @Test
    public void jsonNullableWithValidatorIsNull() {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    import ru.tinkoff.kora.json.common.JsonNullable;
                    @Valid
                    record TestRecord(@NotEmpty JsonNullable<String> value) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();
        assertThat(validatorClass.getConstructors()).hasSize(1);

        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator", notEmptyStringConstraintFactory());
        var violations = validator.validate(newObject("TestRecord", JsonNullable.nullValue()));
        assertEquals(1, violations.size());
    }

    @Test
    public void jsonNullableWithValidatorIsPresent() {
        compile(List.of(new KoraAppProcessor(), new ValidAnnotationProcessor()),
            """
                    import ru.tinkoff.kora.json.common.JsonNullable;
                    @Valid
                    record TestRecord(@NotEmpty JsonNullable<String> value) {}
                    """);
        compileResult.assertSuccess();

        var validatorClass = compileResult.loadClass("$TestRecord_Validator");
        assertThat(validatorClass).isNotNull();
        assertThat(validatorClass.getConstructors()).hasSize(1);

        Validator<Object> validator = (Validator<Object>) newObject("$TestRecord_Validator", notEmptyStringConstraintFactory());
        var violations = validator.validate(newObject("TestRecord", JsonNullable.of("1")));
        assertEquals(0, violations.size());
    }
}
