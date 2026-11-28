package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.FutureOrPresentValidatorFactory;
import java.time.Instant;

public final class FutureOrPresentInstantValidatorFactoryImpl implements FutureOrPresentValidatorFactory<Instant> {
    public static final FutureOrPresentInstantValidatorFactoryImpl INSTANCE = new FutureOrPresentInstantValidatorFactoryImpl();

    @Override
    public Validator<Instant> create() {
        return new TemporalValidator<>(TemporalValidator.Rule.FUTURE_OR_PRESENT, InstantNowSupplier.INSTANCE);
    }
}
