package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.PastOrPresentValidatorFactory;
import java.time.Instant;

public final class PastOrPresentInstantValidatorFactoryImpl implements PastOrPresentValidatorFactory<Instant> {
    public static final PastOrPresentInstantValidatorFactoryImpl INSTANCE = new PastOrPresentInstantValidatorFactoryImpl();

    @Override
    public Validator<Instant> create() {
        return new TemporalValidator<>(TemporalValidator.Rule.PAST_OR_PRESENT, InstantNowSupplier.INSTANCE);
    }
}
