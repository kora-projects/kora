package io.koraframework.validation.common.constraint;

import java.time.OffsetDateTime;
import java.util.function.Supplier;

public final class OffsetDateTimeNowSupplier implements Supplier<OffsetDateTime> {
    public static final OffsetDateTimeNowSupplier INSTANCE = new OffsetDateTimeNowSupplier();

    @Override
    public OffsetDateTime get() {
        return OffsetDateTime.now();
    }
}
