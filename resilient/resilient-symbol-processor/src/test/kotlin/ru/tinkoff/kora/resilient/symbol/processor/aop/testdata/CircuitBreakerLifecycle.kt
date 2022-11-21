package ru.tinkoff.kora.resilient.symbol.processor.aop.testdata

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle

data class CircuitBreakerLifecycle(val target: CircuitBreakerTarget, val targetFallback: CircuitBreakerFallbackTarget) : MockLifecycle
