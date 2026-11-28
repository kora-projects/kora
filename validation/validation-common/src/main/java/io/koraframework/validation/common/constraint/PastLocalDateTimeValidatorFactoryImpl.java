package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.PastValidatorFactory;
import java.time.LocalDateTime;

public final class PastLocalDateTimeValidatorFactoryImpl implements PastValidatorFactory<LocalDateTime> {
    public static final PastLocalDateTimeValidatorFactoryImpl INSTANCE = new PastLocalDateTimeValidatorFactoryImpl();

    @Override
    public Validator<LocalDateTime> create() {
        return new TemporalValidator<>(TemporalValidator.Rule.PAST, LocalDateTimeNowSupplier.INSTANCE);
    }
}
