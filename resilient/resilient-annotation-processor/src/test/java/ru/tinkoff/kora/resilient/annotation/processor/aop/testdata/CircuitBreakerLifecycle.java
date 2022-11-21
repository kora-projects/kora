package ru.tinkoff.kora.resilient.annotation.processor.aop.testdata;

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;

public record CircuitBreakerLifecycle(CircuitBreakerTarget target, CircuitBreakerFallbackTarget fallbackTarget) implements MockLifecycle {
}
