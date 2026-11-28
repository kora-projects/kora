package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.PastValidatorFactory;
import java.time.Instant;

public final class PastInstantValidatorFactoryImpl implements PastValidatorFactory<Instant> {
    public static final PastInstantValidatorFactoryImpl INSTANCE = new PastInstantValidatorFactoryImpl();

    @Override
    public Validator<Instant> create() {
        return new TemporalValidator<>(TemporalValidator.Rule.PAST, InstantNowSupplier.INSTANCE);
    }
}
