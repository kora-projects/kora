package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.PastValidatorFactory;
import java.time.LocalDate;

public final class PastLocalDateValidatorFactoryImpl implements PastValidatorFactory<LocalDate> {
    public static final PastLocalDateValidatorFactoryImpl INSTANCE = new PastLocalDateValidatorFactoryImpl();

    @Override
    public Validator<LocalDate> create() {
        return new TemporalValidator<>(TemporalValidator.Rule.PAST, LocalDateNowSupplier.INSTANCE);
    }
}
