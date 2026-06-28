package io.koraframework.resilient.fallback;

import io.koraframework.resilient.fallback.telemetry.FallbackTelemetry;

import java.util.function.Supplier;

final class KoraFallback implements Fallback {

    private final String name;
    private final FallbackTelemetry telemetry;
    private final FallbackPredicate failurePredicate;
    private final FallbackConfig.NamedConfig config;

    KoraFallback(String name, FallbackTelemetry telemetry, FallbackPredicate failurePredicate, FallbackConfig.NamedConfig config) {
        this.name = name;
        this.telemetry = telemetry;
        this.failurePredicate = failurePredicate;
        this.config = config;
    }

    @Override
    public boolean canFallback(Throwable throwable) {
        var observation = this.telemetry.observe();
        try {
            if (Boolean.FALSE.equals(config.enabled())) {
                return false;
            } else if (failurePredicate.test(throwable)) {
                observation.recordExecute(throwable);
                return true;
            } else {
                return false;
            }
        } catch (Throwable e) {
            observation.observeError(e);
            throw e;
        } finally {
            observation.end();
        }
    }

    @Override
    public void fallback(Runnable runnable, Runnable fallback) {
        if (Boolean.FALSE.equals(config.enabled())) {
            runnable.run();
            return;
        }

        try {
            runnable.run();
        } catch (Throwable e) {
            if (canFallback(e)) {
                fallback.run();
            } else {
                throw e;
            }
        }
    }

    @Override
    public <T> T fallback(Supplier<T> supplier, Supplier<T> fallback) {
        if (Boolean.FALSE.equals(config.enabled())) {
            return supplier.get();
        }

        try {
            return supplier.get();
        } catch (Throwable e) {
            if (canFallback(e)) {
                return fallback.get();
            } else {
                throw e;
            }
        }
    }
}
