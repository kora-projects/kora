package ru.tinkoff.kora.resilient.retry;


import javax.annotation.Nonnull;

final class KoraRetryPredicate implements RetryPredicate {

    @Nonnull
    @Override
    public String name() {
        return KoraRetryPredicate.class.getCanonicalName();
    }

    @Override
    public boolean test(@Nonnull Throwable throwable) {
        return true;
    }
}
