package io.koraframework.resilient.bulkhead;

import io.koraframework.application.graph.Lifecycle;
import io.koraframework.resilient.bulkhead.telemetry.BulkheadTelemetry;
import io.koraframework.resilient.common.ThrowableCallable;
import io.koraframework.resilient.common.ThrowableRunnable;
import java.util.concurrent.CompletionStage;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;

/**
 * Configured bulkhead facade that delegates admission, execution helpers, telemetry and lifecycle
 * management to the implementation selected by {@link BulkheadConfig#type()}.
 * <ul>
 * <li>{@link FixedKoraBulkhead}: a constant budget for a resource with known safe concurrency.</li>
 * <li>{@link AimdKoraBulkhead}: gradual growth under demand and multiplicative backoff when
 * operation failures or duration indicate congestion.</li>
 * <li>{@link ThroughputKoraBulkhead}: concurrency probes retained only when sampled completion
 * throughput improves enough, with the same congestion backoff as AIMD.</li>
 * </ul>
 * <p>
 * Use this facade through a typed bulkhead spec rather than constructing a strategy directly. All
 * strategies share the configured queue capacity and wait timeout. Closing the lifecycle rejects
 * waiting and subsequent calls; admitted operations retain their permits until they finish and
 * release them.
 */
public class KoraBulkhead implements Bulkhead, Lifecycle {

    private final AbstractKoraBulkhead delegate;

    public KoraBulkhead(String name, BulkheadConfig config, BulkheadTelemetry telemetry) {
        this(name, config, telemetry, System::nanoTime);
    }

    KoraBulkhead(String name, BulkheadConfig config, BulkheadTelemetry telemetry, LongSupplier clock) {
        BulkheadConfig.validate(name, config);
        this.delegate = switch (config.type()) {
            case FIXED -> new FixedKoraBulkhead(name, config, telemetry, clock);
            case AIMD -> new AimdKoraBulkhead(name, config, telemetry, clock);
            case THROUGHPUT -> new ThroughputKoraBulkhead(name, config, telemetry, clock);
        };
    }

    @Override
    public String name() {
        return this.delegate.name();
    }

    @Override
    public int maxConcurrentCalls() {
        return this.delegate.maxConcurrentCalls();
    }

    @Override
    public int currentLimit() {
        return this.delegate.currentLimit();
    }

    @Override
    public int queueLength() {
        return this.delegate.queueLength();
    }

    @Override
    public int inFlight() {
        return this.delegate.inFlight();
    }

    @Override
    public long rejectedCalls() {
        return this.delegate.rejectedCalls();
    }

    @Override
    public @Nullable Permit tryAcquire() {
        return this.delegate.tryAcquire();
    }

    @Override
    public Permit acquire() {
        return this.delegate.acquire();
    }

    @Override
    public CompletionStage<Permit> acquireAsync() {
        return this.delegate.acquireAsync();
    }

    @Override
    public <T, E extends Throwable> T execute(ThrowableCallable<T, E> action) throws E {
        return this.delegate.execute(action);
    }

    @Override
    public <E extends Throwable> void execute(ThrowableRunnable<E> action) throws E {
        this.delegate.execute(action);
    }

    @Override
    public <T, E extends Throwable> CompletionStage<T> executeAsync(ThrowableCallable<? extends CompletionStage<T>, E> action) throws E {
        return this.delegate.executeAsync(action);
    }

    @Override
    public void init() {
        this.delegate.init();
    }

    @Override
    public void release() {
        this.delegate.release();
    }

    Bulkhead delegate() {
        return this.delegate;
    }
}
