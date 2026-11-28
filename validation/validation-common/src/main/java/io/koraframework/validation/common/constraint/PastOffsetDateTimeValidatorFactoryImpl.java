package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.PastValidatorFactory;
import java.time.OffsetDateTime;

public final class PastOffsetDateTimeValidatorFactoryImpl implements PastValidatorFactory<OffsetDateTime> {
    public static final PastOffsetDateTimeValidatorFactoryImpl INSTANCE = new PastOffsetDateTimeValidatorFactoryImpl();

    @Override
    public Validator<OffsetDateTime> create() {
        return new TemporalValidator<>(TemporalValidator.Rule.PAST, OffsetDateTimeNowSupplier.INSTANCE);
    }
}
