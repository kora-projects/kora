package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.NotEmptyValidatorFactory;

public final class NotEmptyCharSequenceValidatorFactoryImpl implements NotEmptyValidatorFactory<CharSequence> {
    public static final NotEmptyCharSequenceValidatorFactoryImpl INSTANCE = new NotEmptyCharSequenceValidatorFactoryImpl();

    @Override
    public Validator<CharSequence> create() {
        return new NotEmptyStringValidator<>();
    }
}
