package io.koraframework.cache.caffeine.telemetry;

public interface CaffeineCacheTelemetry {

    CaffeineCacheObservation observe(Operation operation);

    enum Operation {
        GET,
        GET_MANY,
        GET_ALL,
        PUT,
        PUT_MANY,
        COMPUTE_IF_ABSENT,
        COMPUTE_IF_ABSENT_MANY,
        INVALIDATE,
        INVALIDATE_MANY,
        INVALIDATE_ALL;

        public boolean hasCacheResult() {
            return switch (this) {
                case GET, GET_MANY, GET_ALL, COMPUTE_IF_ABSENT, COMPUTE_IF_ABSENT_MANY -> true;
                default -> false;
            };
        }
    }
}
