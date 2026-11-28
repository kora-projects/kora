package io.koraframework.validation.common.constraint;

import java.time.LocalDateTime;
import java.util.function.Supplier;

public final class LocalDateTimeNowSupplier implements Supplier<LocalDateTime> {
    public static final LocalDateTimeNowSupplier INSTANCE = new LocalDateTimeNowSupplier();

    @Override
    public LocalDateTime get() {
        return LocalDateTime.now();
    }
}
