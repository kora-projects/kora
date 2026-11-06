package io.koraframework.validation.annotation.processor;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.ValidatorModule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ValidationPojoAccessorTest extends AbstractValidationAnnotationProcessorTest {

    @Test
    @SuppressWarnings("unchecked")
    public void pojoPrimitiveBooleanFieldValidatedWithIsGetter() throws Exception {
        var compileResult = compile(List.of(new ValidAnnotationProcessor()),
            """
                @Valid
                public class Consent {
                    @AssertTrue
                    private boolean accepted;
                    public boolean isAccepted() { return accepted; }
                    public void setAccepted(boolean accepted) { this.accepted = accepted; }
                }
                """);
        compileResult.assertSuccess();

        var validator = (Validator<Object>) compileResult.loadClass("$Consent_Validator").getConstructors()[0]
            .newInstance(new ValidatorModule() {}.assertTrueValidatorFactory());
        var consentClass = compileResult.loadClass("Consent");
        var consent = consentClass.getConstructor().newInstance();

        assertThat(validator.validate(consent)).hasSize(1);

        consentClass.getMethod("setAccepted", boolean.class).invoke(consent, true);
        assertThat(validator.validate(consent)).isEmpty();
    }
}
