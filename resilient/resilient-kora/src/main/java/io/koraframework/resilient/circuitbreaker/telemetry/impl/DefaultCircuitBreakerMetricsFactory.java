package io.koraframework.resilient.circuitbreaker.telemetry.impl;

import io.koraframework.resilient.circuitbreaker.CircuitBreaker;
import io.koraframework.resilient.circuitbreaker.telemetry.CircuitBreakerObservation;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.BaseUnits;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class DefaultCircuitBreakerMetricsFactory {

    public static final DefaultCircuitBreakerMetricsFactory INSTANCE = new DefaultCircuitBreakerMetricsFactory();

    public DefaultCircuitBreakerMetrics create(DefaultCircuitBreakerTelemetry.TelemetryContext context) {
        return new DefaultCircuitBreakerMetrics(context);
    }

    public static class DefaultCircuitBreakerMetrics {

        public record StateKey(String name,
                               @Nullable Tags extraTags) {

            public StateKey withExtraTags(Tags tags) {
                return new StateKey(name, tags);
            }
        }

        public record TransitionKey(String name,
                                    CircuitBreaker.State state,
                                    @Nullable Tags extraTags) {

            public TransitionKey withExtraTags(Tags tags) {
                return new TransitionKey(name, state, tags);
            }
        }

        public record AcquireKey(String name,
                                 CircuitBreaker.State state,
                                 CircuitBreakerObservation.CallAcquireStatus status,
                                 @Nullable Tags extraTags) {

            public AcquireKey withExtraTags(Tags tags) {
                return new AcquireKey(name, state, status, tags);
            }
        }

        public record ResultKey(String name,
                                CircuitBreaker.State state,
                                CircuitBreakerObservation.CallResult result,
                                @Nullable Tags extraTags) {

            public ResultKey withExtraTags(Tags tags) {
                return new ResultKey(name, state, result, tags);
            }
        }

        protected final ConcurrentHashMap<StateKey, AtomicInteger> stateValueCache = new ConcurrentHashMap<>();
        protected final ConcurrentHashMap<StateKey, Gauge> stateCache = new ConcurrentHashMap<>();
        protected final ConcurrentHashMap<TransitionKey, Counter> transitionCache = new ConcurrentHashMap<>();
        protected final ConcurrentHashMap<AcquireKey, Counter> acquireCache = new ConcurrentHashMap<>();
        protected final ConcurrentHashMap<ResultKey, Counter> resultCache = new ConcurrentHashMap<>();
        protected final DefaultCircuitBreakerTelemetry.TelemetryContext context;

        public DefaultCircuitBreakerMetrics(DefaultCircuitBreakerTelemetry.TelemetryContext context) {
            this.context = context;
        }

        public void recordCallAcquire(CircuitBreaker.State state, CircuitBreakerObservation.CallAcquireStatus callStatus) {
            if (state == CircuitBreaker.State.HALF_OPEN) {
                var key = createMetricAcquireKey(state, callStatus);
                var meter = this.acquireCache.computeIfAbsent(key, k -> createMetricAcquire(k).register(this.context.meterRegistry()));
                meter.increment();
            }
            if (state == CircuitBreaker.State.OPEN && callStatus == CircuitBreakerObservation.CallAcquireStatus.REJECTED) {
                var key = createMetricAcquireKey(state, callStatus);
                var meter = this.acquireCache.computeIfAbsent(key, k -> createMetricAcquire(k).register(this.context.meterRegistry()));
                meter.increment();
            }
        }

        public void recordCallResult(CircuitBreaker.State state, CircuitBreakerObservation.CallResult callResult) {
            var key = createMetricResultKey(state, callResult);
            var meter = this.resultCache.computeIfAbsent(key, k -> createMetricResult(k).register(this.context.meterRegistry()));
            meter.increment();
        }

        public void recordState(CircuitBreaker.State newState) {
            var stateKey = createMetricStateKey(newState);
            var stateValue = this.stateValueCache.computeIfAbsent(stateKey, _ -> new AtomicInteger(asIntState(newState)));
            this.stateCache.computeIfAbsent(stateKey, k -> createMetricState(k, stateValue).register(this.context.meterRegistry()));
            stateValue.set(asIntState(newState));

            if (newState == CircuitBreaker.State.OPEN || newState == CircuitBreaker.State.HALF_OPEN) {
                var transitionKey = createMetricTransitionKey(newState);
                var transition = this.transitionCache.computeIfAbsent(transitionKey, k -> createMetricTransition(k).register(this.context.meterRegistry()));
                transition.increment();
            }
        }

        protected StateKey createMetricStateKey(CircuitBreaker.State initialState) {
            return new StateKey(this.context.name(), null);
        }

        protected TransitionKey createMetricTransitionKey(CircuitBreaker.State state) {
            return new TransitionKey(this.context.name(), state, null);
        }

        protected AcquireKey createMetricAcquireKey(CircuitBreaker.State state, CircuitBreakerObservation.CallAcquireStatus callStatus) {
            return new AcquireKey(this.context.name(), state, callStatus, null);
        }

        protected ResultKey createMetricResultKey(CircuitBreaker.State state, CircuitBreakerObservation.CallResult callResult) {
            return new ResultKey(this.context.name(), state, callResult, null);
        }

        // DO NOT ADD DYNAMIC TAGS IN BUILDER, use metric key instead of metric collision will happen
        protected Gauge.Builder<?> createMetricState(StateKey metricKey, AtomicInteger stateValue) {
            return Gauge.builder("resilient.circuitbreaker.state", stateValue::get)
                .tags(Tags.of(createTags(metricKey.name, null, null, metricKey.extraTags)))
                .description("Circuit Breaker state metrics, where 0 -> CLOSED, 1 -> HALF_OPEN, 2 -> OPEN");
        }

        // DO NOT ADD DYNAMIC TAGS IN BUILDER, use metric key instead of metric collision will happen
        protected Counter.Builder createMetricTransition(TransitionKey metricKey) {
            return Counter.builder("resilient.circuitbreaker.transition")
                .baseUnit(BaseUnits.OPERATIONS)
                .tags(Tags.of(createTags(metricKey.name, metricKey.state.name(), null, metricKey.extraTags)));
        }

        // DO NOT ADD DYNAMIC TAGS IN BUILDER, use metric key instead of metric collision will happen
        protected Counter.Builder createMetricAcquire(AcquireKey metricKey) {
            return Counter.builder("resilient.circuitbreaker.call.acquire")
                .baseUnit(BaseUnits.OPERATIONS)
                .tags(Tags.of(createTags(metricKey.name, metricKey.state.name(), metricKey.status.name(), metricKey.extraTags)));
        }

        // DO NOT ADD DYNAMIC TAGS IN BUILDER, use metric key instead of metric collision will happen
        protected Counter.Builder createMetricResult(ResultKey metricKey) {
            return Counter.builder("resilient.circuitbreaker.call.result")
                .baseUnit(BaseUnits.OPERATIONS)
                .tags(Tags.of(createTags(metricKey.name, metricKey.state.name(), metricKey.result.name(), metricKey.extraTags)));
        }

        protected ArrayList<Tag> createTags(String name, @Nullable String state, @Nullable String status, @Nullable Tags extraTags) {
            var extraTagsCount = 0;
            if (extraTags != null) {
                for (Tag _ : extraTags) {
                    extraTagsCount++;
                }
            }
            var tags = new ArrayList<Tag>(1 + (state == null ? 0 : 1) + (status == null ? 0 : 1) + this.context.config().metrics().tags().size() + extraTagsCount);
            tags.add(Tag.of("name", name));
            if (state != null) {
                tags.add(Tag.of("state", state));
            }
            if (status != null) {
                tags.add(Tag.of("status", status));
            }
            for (var tag : this.context.config().metrics().tags().entrySet()) {
                tags.add(Tag.of(tag.getKey(), tag.getValue()));
            }
            if (extraTags != null) {
                for (Tag extraTag : extraTags) {
                    tags.add(extraTag);
                }
            }
            return tags;
        }

        protected int asIntState(CircuitBreaker.State state) {
            return switch (state) {
                case CLOSED -> 0;
                case HALF_OPEN -> 1;
                case OPEN -> 2;
            };
        }
    }
}
