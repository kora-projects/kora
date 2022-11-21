package ru.tinkoff.kora.common.readiness;

import reactor.core.publisher.Mono;

public interface ReadinessProbe {
    /**
     * Perform readiness probe
     * @return Mono.empty() if probe succeeds or probe failure
     */
    Mono<ReadinessProbeFailure> probe();
}
