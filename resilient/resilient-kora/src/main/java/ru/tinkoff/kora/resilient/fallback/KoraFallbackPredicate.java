package ru.tinkoff.kora.resilient.fallback;


final class KoraFallbackPredicate implements FallbackPredicate {

    @Override
    public String name() {
        return KoraFallbackPredicate.class.getCanonicalName();
    }

    @Override
    public boolean test(Throwable throwable) {
        return true;
    }
}
