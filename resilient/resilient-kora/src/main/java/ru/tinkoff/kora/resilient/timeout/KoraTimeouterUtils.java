package ru.tinkoff.kora.resilient.timeout;

final class KoraTimeouterUtils {

    private KoraTimeouterUtils() {}

    static void doThrow(Throwable e) {
        KoraTimeouterUtils.doThrow0(e);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Exception> void doThrow0(Throwable e) throws E {
        throw (E) e;
    }
}
