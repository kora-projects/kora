package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.FutureOrPresentValidatorFactory;
import java.time.ZonedDateTime;

public final class FutureOrPresentZonedDateTimeValidatorFactoryImpl implements FutureOrPresentValidatorFactory<ZonedDateTime> {
    public static final FutureOrPresentZonedDateTimeValidatorFactoryImpl INSTANCE = new FutureOrPresentZonedDateTimeValidatorFactoryImpl();

    @Override
    public Validator<ZonedDateTime> create() {
        return new TemporalValidator<>(TemporalValidator.Rule.FUTURE_OR_PRESENT, ZonedDateTimeNowSupplier.INSTANCE);
    }
}
