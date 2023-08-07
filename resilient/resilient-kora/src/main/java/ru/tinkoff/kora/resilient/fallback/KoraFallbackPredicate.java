package ru.tinkoff.kora.resilient.fallback;


import javax.annotation.Nonnull;

final class KoraFallbackPredicate implements FallbackPredicate {

    @Nonnull
    @Override
    public String name() {
        return KoraFallbackPredicate.class.getCanonicalName();
    }

    @Override
    public boolean test(@Nonnull Throwable throwable) {
        return true;
    }
}
