package io.koraframework.validation.common.constraint;

import java.time.ZonedDateTime;
import java.util.function.Supplier;

public final class ZonedDateTimeNowSupplier implements Supplier<ZonedDateTime> {
    public static final ZonedDateTimeNowSupplier INSTANCE = new ZonedDateTimeNowSupplier();

    @Override
    public ZonedDateTime get() {
        return ZonedDateTime.now();
    }
}
