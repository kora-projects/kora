package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.FutureValidatorFactory;
import java.time.LocalDateTime;

public final class FutureLocalDateTimeValidatorFactoryImpl implements FutureValidatorFactory<LocalDateTime> {
    public static final FutureLocalDateTimeValidatorFactoryImpl INSTANCE = new FutureLocalDateTimeValidatorFactoryImpl();

    @Override
    public Validator<LocalDateTime> create() {
        return new TemporalValidator<>(TemporalValidator.Rule.FUTURE, LocalDateTimeNowSupplier.INSTANCE);
    }
}
