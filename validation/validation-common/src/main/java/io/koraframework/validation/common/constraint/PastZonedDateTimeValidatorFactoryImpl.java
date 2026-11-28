package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.PastValidatorFactory;
import java.time.ZonedDateTime;

public final class PastZonedDateTimeValidatorFactoryImpl implements PastValidatorFactory<ZonedDateTime> {
    public static final PastZonedDateTimeValidatorFactoryImpl INSTANCE = new PastZonedDateTimeValidatorFactoryImpl();

    @Override
    public Validator<ZonedDateTime> create() {
        return new TemporalValidator<>(TemporalValidator.Rule.PAST, ZonedDateTimeNowSupplier.INSTANCE);
    }
}
