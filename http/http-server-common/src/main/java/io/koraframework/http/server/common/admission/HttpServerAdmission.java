package io.koraframework.http.server.common.admission;

import io.koraframework.http.server.common.request.HttpServerRequest;
import org.jspecify.annotations.Nullable;

/**
 * Controls admission to HTTP request processing independently of the server transport.
 */
public interface HttpServerAdmission {

    /**
     * Acquires admission for a routed request. Implementations may wait for a bounded duration,
     * so this method must be called on a thread that permits blocking.
     *
     * @return a permit held until the HTTP exchange completes, or {@code null} to reject the request
     */
    @Nullable
    Permit acquire(HttpServerRequest request);

    interface Permit extends AutoCloseable {

        /**
         * Records a processing or response error for admission feedback.
         * Must tolerate feedback from different threads and concurrent release.
         */
        void observeError(Throwable error);

        /**
         * Releases admission. Must be idempotent and safe to call from a different thread.
         * HTTP completion does not guarantee that detached application work has stopped.
         */
        @Override
        void close();
    }
}
