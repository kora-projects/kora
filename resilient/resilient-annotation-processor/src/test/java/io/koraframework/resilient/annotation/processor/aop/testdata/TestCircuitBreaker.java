package io.koraframework.resilient.annotation.processor.aop.testdata;

import io.koraframework.resilient.circuitbreaker.annotation.CircuitBreaker;

@CircuitBreaker("resilient.circuitbreaker.custom1")
public interface TestCircuitBreaker extends io.koraframework.resilient.circuitbreaker.CircuitBreaker {}
