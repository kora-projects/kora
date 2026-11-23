package io.koraframework.scheduling.common;

/**
 * Helpers for generated scheduled job code.
 */
public final class SchedulingUtils {

    private SchedulingUtils() {}

    /**
     * Rethrows any exception unchanged from a job interface that can't declare checked exceptions,
     * so the scheduler sees the exception the scheduled method threw (e.g. a Quartz {@code JobExecutionException}).
     *
     * @return never returns, declared to be used as {@code throw SchedulingUtils.sneakyThrow(e)}
     */
    @SuppressWarnings("unchecked")
    public static <E extends Throwable> RuntimeException sneakyThrow(Throwable e) throws E {
        throw (E) e;
    }
}
