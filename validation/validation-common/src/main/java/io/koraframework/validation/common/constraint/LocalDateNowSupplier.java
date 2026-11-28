package io.koraframework.validation.common.constraint;

import java.time.LocalDate;
import java.util.function.Supplier;

public final class LocalDateNowSupplier implements Supplier<LocalDate> {
    public static final LocalDateNowSupplier INSTANCE = new LocalDateNowSupplier();

    @Override
    public LocalDate get() {
        return LocalDate.now();
    }
}
