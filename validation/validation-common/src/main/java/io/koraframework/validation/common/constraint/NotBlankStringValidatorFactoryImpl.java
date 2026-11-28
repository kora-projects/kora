package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.NotBlankValidatorFactory;

public final class NotBlankStringValidatorFactoryImpl implements NotBlankValidatorFactory<String> {
    public static final NotBlankStringValidatorFactoryImpl INSTANCE = new NotBlankStringValidatorFactoryImpl();

    @Override
    public Validator<String> create() {
        return new NotBlankStringValidator<>();
    }
}
