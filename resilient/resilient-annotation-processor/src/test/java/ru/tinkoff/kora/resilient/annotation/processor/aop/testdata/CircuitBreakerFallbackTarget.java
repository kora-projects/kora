package ru.tinkoff.kora.resilient.annotation.processor.aop.testdata;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.resilient.circuitbreaker.annotation.CircuitBreaker;

@Component
public class CircuitBreakerFallbackTarget implements MockLifecycle {

    public static final String VALUE = "OK";
    public static final String FALLBACK = "FALLBACK";

    public boolean alwaysFail = true;

    @CircuitBreaker(value = "custom_fallback1", fallbackMethod = "getFallbackSync")
    public String getValueSync() {
        if (alwaysFail)
            throw new IllegalStateException("Failed");

        return VALUE;
    }

    protected String getFallbackSync() {
        return FALLBACK;
    }

    @CircuitBreaker(value = "custom_fallback2", fallbackMethod = "getFallbackMono")
    public Mono<String> getValueMono() {
        if (alwaysFail)
            return Mono.error(new IllegalStateException("Failed"));

        return Mono.just(VALUE);
    }

    protected Mono<String> getFallbackMono() {
        return Mono.just(FALLBACK);
    }

    @CircuitBreaker(value = "custom_fallback3", fallbackMethod = "getFallbackFlux")
    public Flux<String> getValueFlux() {
        if (alwaysFail)
            return Flux.error(new IllegalStateException("Failed"));

        return Flux.just(VALUE);
    }

    protected Flux<String> getFallbackFlux() {
        return Flux.just(FALLBACK);
    }
}
