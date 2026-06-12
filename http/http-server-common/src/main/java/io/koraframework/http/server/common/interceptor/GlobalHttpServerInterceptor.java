package io.koraframework.http.server.common.interceptor;

public interface GlobalHttpServerInterceptor extends HttpServerInterceptor {

    int HIGHEST = 500_000;
    int ERRORS = 400_000;
    int SECURITY = 300_000;
    int VALIDATION = 200_000;
    int NORMAL = 0;
    int LOWEST = -500_000;

    /**
     * Higher priority means outer interceptor.
     * <p>
     * Request processing order:
     *   higher priority -> lower priority -> handler
     * <p>
     * Response/error processing order:
     *   handler -> lower priority -> higher priority
     *
     * @return the bigger priority the better position is in global interceptors list
     */
    default int priority() {
        return NORMAL;
    }
}
