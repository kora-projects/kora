package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.PastOrPresentValidatorFactory;
import java.time.LocalDate;

public final class PastOrPresentLocalDateValidatorFactoryImpl implements PastOrPresentValidatorFactory<LocalDate> {
    public static final PastOrPresentLocalDateValidatorFactoryImpl INSTANCE = new PastOrPresentLocalDateValidatorFactoryImpl();

    @Override
    public Validator<LocalDate> create() {
        return new TemporalValidator<>(TemporalValidator.Rule.PAST_OR_PRESENT, LocalDateNowSupplier.INSTANCE);
    }
}
