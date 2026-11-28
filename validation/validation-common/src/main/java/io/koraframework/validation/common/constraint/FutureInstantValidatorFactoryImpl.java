package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.FutureValidatorFactory;
import java.time.Instant;

public final class FutureInstantValidatorFactoryImpl implements FutureValidatorFactory<Instant> {
    public static final FutureInstantValidatorFactoryImpl INSTANCE = new FutureInstantValidatorFactoryImpl();

    @Override
    public Validator<Instant> create() {
        return new TemporalValidator<>(TemporalValidator.Rule.FUTURE, InstantNowSupplier.INSTANCE);
    }
}
