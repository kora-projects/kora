package ru.tinkoff.kora.common.liveness;

import reactor.core.publisher.Mono;

public interface LivenessProbe {
    /**
     * Perform liveness probe
     * @return Mono.empty() if probe succeeds or probe failure
     */
    Mono<LivenessProbeFailure> probe();
}
