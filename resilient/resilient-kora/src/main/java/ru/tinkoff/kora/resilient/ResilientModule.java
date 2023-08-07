package ru.tinkoff.kora.resilient;

import ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreakerModule;
import ru.tinkoff.kora.resilient.fallback.FallbackModule;
import ru.tinkoff.kora.resilient.retry.RetryModule;
import ru.tinkoff.kora.resilient.timeout.TimeoutModule;

public interface ResilientModule extends CircuitBreakerModule, RetryModule, TimeoutModule, FallbackModule {

}
