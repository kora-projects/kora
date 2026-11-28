package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.FutureValidatorFactory;
import java.time.LocalDate;

public final class FutureLocalDateValidatorFactoryImpl implements FutureValidatorFactory<LocalDate> {
    public static final FutureLocalDateValidatorFactoryImpl INSTANCE = new FutureLocalDateValidatorFactoryImpl();

    @Override
    public Validator<LocalDate> create() {
        return new TemporalValidator<>(TemporalValidator.Rule.FUTURE, LocalDateNowSupplier.INSTANCE);
    }
}
