package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.PastOrPresentValidatorFactory;
import java.time.ZonedDateTime;

public final class PastOrPresentZonedDateTimeValidatorFactoryImpl implements PastOrPresentValidatorFactory<ZonedDateTime> {
    public static final PastOrPresentZonedDateTimeValidatorFactoryImpl INSTANCE = new PastOrPresentZonedDateTimeValidatorFactoryImpl();

    @Override
    public Validator<ZonedDateTime> create() {
        return new TemporalValidator<>(TemporalValidator.Rule.PAST_OR_PRESENT, ZonedDateTimeNowSupplier.INSTANCE);
    }
}
