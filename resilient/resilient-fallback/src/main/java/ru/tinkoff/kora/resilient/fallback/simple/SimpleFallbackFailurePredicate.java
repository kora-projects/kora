package ru.tinkoff.kora.resilient.fallback.simple;

import ru.tinkoff.kora.resilient.fallback.FallbackFailurePredicate;

import javax.annotation.Nonnull;

final class SimpleFallbackFailurePredicate implements FallbackFailurePredicate {

    @Nonnull
    @Override
    public String name() {
        return SimpleFallbackFailurePredicate.class.getCanonicalName();
    }

    @Override
    public boolean test(@Nonnull Throwable throwable) {
        return true;
    }
}
