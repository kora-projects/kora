package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.NotBlankValidatorFactory;

public final class NotBlankCharSequenceValidatorFactoryImpl implements NotBlankValidatorFactory<CharSequence> {
    public static final NotBlankCharSequenceValidatorFactoryImpl INSTANCE = new NotBlankCharSequenceValidatorFactoryImpl();

    @Override
    public Validator<CharSequence> create() {
        return new NotBlankStringValidator<>();
    }
}
