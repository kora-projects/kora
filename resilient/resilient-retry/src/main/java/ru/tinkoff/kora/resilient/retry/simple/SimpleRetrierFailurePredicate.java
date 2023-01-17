package ru.tinkoff.kora.resilient.retry.simple;

import ru.tinkoff.kora.resilient.retry.RetrierFailurePredicate;

import javax.annotation.Nonnull;

final class SimpleRetrierFailurePredicate implements RetrierFailurePredicate {

    @Nonnull
    @Override
    public String name() {
        return SimpleRetrierFailurePredicate.class.getCanonicalName();
    }

    @Override
    public boolean test(@Nonnull Throwable throwable) {
        return true;
    }
}
