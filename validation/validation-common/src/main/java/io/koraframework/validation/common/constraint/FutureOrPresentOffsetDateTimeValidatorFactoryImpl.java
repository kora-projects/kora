package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.FutureOrPresentValidatorFactory;
import java.time.OffsetDateTime;

public final class FutureOrPresentOffsetDateTimeValidatorFactoryImpl implements FutureOrPresentValidatorFactory<OffsetDateTime> {
    public static final FutureOrPresentOffsetDateTimeValidatorFactoryImpl INSTANCE = new FutureOrPresentOffsetDateTimeValidatorFactoryImpl();

    @Override
    public Validator<OffsetDateTime> create() {
        return new TemporalValidator<>(TemporalValidator.Rule.FUTURE_OR_PRESENT, OffsetDateTimeNowSupplier.INSTANCE);
    }
}
