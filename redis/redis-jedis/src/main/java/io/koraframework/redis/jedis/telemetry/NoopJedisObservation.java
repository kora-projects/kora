package io.koraframework.redis.jedis.telemetry;

import io.opentelemetry.api.trace.Span;

public final class NoopJedisObservation implements JedisObservation {

    public static final NoopJedisObservation INSTANCE = new NoopJedisObservation();

    private NoopJedisObservation() { }

    @Override
    public Span span() {
        return Span.getInvalid();
    }

    @Override
    public void end() {

    }

    @Override
    public void observeResult(Object result) {

    }

    @Override
    public void observeError(Throwable e) {

    }
}
