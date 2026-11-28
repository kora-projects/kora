package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.AssertTrueValidatorFactory;

public final class AssertTrueValidatorFactoryImpl implements AssertTrueValidatorFactory<Boolean> {
    public static final AssertTrueValidatorFactoryImpl INSTANCE = new AssertTrueValidatorFactoryImpl();

    @Override
    public Validator<Boolean> create() {
        return new BooleanValidator(true);
    }
}
