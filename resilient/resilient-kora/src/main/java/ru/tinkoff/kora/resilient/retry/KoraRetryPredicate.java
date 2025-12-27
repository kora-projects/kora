package ru.tinkoff.kora.resilient.retry;


final class KoraRetryPredicate implements RetryPredicate {

    @Override
    public String name() {
        return KoraRetryPredicate.class.getCanonicalName();
    }

    @Override
    public boolean test(Throwable throwable) {
        return true;
    }
}
