package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.AssertFalseValidatorFactory;

public final class AssertFalseValidatorFactoryImpl implements AssertFalseValidatorFactory<Boolean> {
    public static final AssertFalseValidatorFactoryImpl INSTANCE = new AssertFalseValidatorFactoryImpl();

    @Override
    public Validator<Boolean> create() {
        return new BooleanValidator(false);
    }
}
