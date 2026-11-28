package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.PastOrPresentValidatorFactory;
import java.time.LocalDateTime;

public final class PastOrPresentLocalDateTimeValidatorFactoryImpl implements PastOrPresentValidatorFactory<LocalDateTime> {
    public static final PastOrPresentLocalDateTimeValidatorFactoryImpl INSTANCE = new PastOrPresentLocalDateTimeValidatorFactoryImpl();

    @Override
    public Validator<LocalDateTime> create() {
        return new TemporalValidator<>(TemporalValidator.Rule.PAST_OR_PRESENT, LocalDateTimeNowSupplier.INSTANCE);
    }
}
