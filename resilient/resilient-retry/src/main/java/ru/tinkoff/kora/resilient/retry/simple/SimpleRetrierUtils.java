package ru.tinkoff.kora.resilient.retry.simple;

public final class SimpleRetrierUtils {

    private SimpleRetrierUtils() {}

    public static void doThrow(Throwable e) {
        SimpleRetrierUtils.doThrow0(e);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Exception> void doThrow0(Throwable e) throws E {
        throw (E) e;
    }
}
