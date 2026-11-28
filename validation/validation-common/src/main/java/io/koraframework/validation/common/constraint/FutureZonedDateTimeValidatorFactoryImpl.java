package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.FutureValidatorFactory;
import java.time.ZonedDateTime;

public final class FutureZonedDateTimeValidatorFactoryImpl implements FutureValidatorFactory<ZonedDateTime> {
    public static final FutureZonedDateTimeValidatorFactoryImpl INSTANCE = new FutureZonedDateTimeValidatorFactoryImpl();

    @Override
    public Validator<ZonedDateTime> create() {
        return new TemporalValidator<>(TemporalValidator.Rule.FUTURE, ZonedDateTimeNowSupplier.INSTANCE);
    }
}
