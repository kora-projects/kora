package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.NotEmptyValidatorFactory;

public final class NotEmptyStringValidatorFactoryImpl implements NotEmptyValidatorFactory<String> {
    public static final NotEmptyStringValidatorFactoryImpl INSTANCE = new NotEmptyStringValidatorFactoryImpl();

    @Override
    public Validator<String> create() {
        return new NotEmptyStringValidator<>();
    }
}
