package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.PastOrPresentValidatorFactory;
import java.time.OffsetDateTime;

public final class PastOrPresentOffsetDateTimeValidatorFactoryImpl implements PastOrPresentValidatorFactory<OffsetDateTime> {
    public static final PastOrPresentOffsetDateTimeValidatorFactoryImpl INSTANCE = new PastOrPresentOffsetDateTimeValidatorFactoryImpl();

    @Override
    public Validator<OffsetDateTime> create() {
        return new TemporalValidator<>(TemporalValidator.Rule.PAST_OR_PRESENT, OffsetDateTimeNowSupplier.INSTANCE);
    }
}
