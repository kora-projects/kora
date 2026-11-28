package io.koraframework.validation.common.constraint;

import java.time.Instant;
import java.util.function.Supplier;

public final class InstantNowSupplier implements Supplier<Instant> {
    public static final InstantNowSupplier INSTANCE = new InstantNowSupplier();

    @Override
    public Instant get() {
        return Instant.now();
    }
}
