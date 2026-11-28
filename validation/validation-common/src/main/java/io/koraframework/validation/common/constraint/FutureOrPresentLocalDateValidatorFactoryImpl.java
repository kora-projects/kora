package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.FutureOrPresentValidatorFactory;
import java.time.LocalDate;

public final class FutureOrPresentLocalDateValidatorFactoryImpl implements FutureOrPresentValidatorFactory<LocalDate> {
    public static final FutureOrPresentLocalDateValidatorFactoryImpl INSTANCE = new FutureOrPresentLocalDateValidatorFactoryImpl();

    @Override
    public Validator<LocalDate> create() {
        return new TemporalValidator<>(TemporalValidator.Rule.FUTURE_OR_PRESENT, LocalDateNowSupplier.INSTANCE);
    }
}
