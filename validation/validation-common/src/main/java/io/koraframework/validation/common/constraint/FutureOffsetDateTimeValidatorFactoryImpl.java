package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.FutureValidatorFactory;
import java.time.OffsetDateTime;

public final class FutureOffsetDateTimeValidatorFactoryImpl implements FutureValidatorFactory<OffsetDateTime> {
    public static final FutureOffsetDateTimeValidatorFactoryImpl INSTANCE = new FutureOffsetDateTimeValidatorFactoryImpl();

    @Override
    public Validator<OffsetDateTime> create() {
        return new TemporalValidator<>(TemporalValidator.Rule.FUTURE, OffsetDateTimeNowSupplier.INSTANCE);
    }
}
