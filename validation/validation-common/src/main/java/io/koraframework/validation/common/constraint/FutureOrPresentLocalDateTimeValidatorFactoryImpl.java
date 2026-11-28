package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.FutureOrPresentValidatorFactory;
import java.time.LocalDateTime;

public final class FutureOrPresentLocalDateTimeValidatorFactoryImpl implements FutureOrPresentValidatorFactory<LocalDateTime> {
    public static final FutureOrPresentLocalDateTimeValidatorFactoryImpl INSTANCE = new FutureOrPresentLocalDateTimeValidatorFactoryImpl();

    @Override
    public Validator<LocalDateTime> create() {
        return new TemporalValidator<>(TemporalValidator.Rule.FUTURE_OR_PRESENT, LocalDateTimeNowSupplier.INSTANCE);
    }
}
