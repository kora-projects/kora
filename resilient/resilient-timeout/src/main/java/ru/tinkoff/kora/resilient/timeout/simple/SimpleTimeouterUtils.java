package ru.tinkoff.kora.resilient.timeout.simple;

public final class SimpleTimeouterUtils {

    private SimpleTimeouterUtils() {}

    public static void doThrow(Throwable e) {
        SimpleTimeouterUtils.doThrow0(e);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Exception> void doThrow0(Throwable e) throws E {
        throw (E) e;
    }
}
