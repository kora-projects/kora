package io.koraframework.common.concurrent;

import io.koraframework.common.Principal;
import io.koraframework.common.context.ThreadContextPropagator;
import io.koraframework.common.telemetry.Observation;
import io.koraframework.common.telemetry.OpentelemetryContext;
import io.opentelemetry.context.Context;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Supplier;

final class KoraContextSnapshot {
    private static final List<ThreadContextPropagator> PROPAGATORS = ServiceLoader
        .load(ThreadContextPropagator.class, ThreadContextPropagator.class.getClassLoader())
        .stream()
        .map(ServiceLoader.Provider::get)
        .toList();

    private final Context opentelemetryContext;
    private final @Nullable Principal principal;
    private final @Nullable Observation observation;
    private final List<ThreadContextPropagator.Snapshot> propagatedContexts;

    private KoraContextSnapshot(
        Context opentelemetryContext,
        @Nullable Principal principal,
        @Nullable Observation observation,
        List<ThreadContextPropagator.Snapshot> propagatedContexts
    ) {
        this.opentelemetryContext = opentelemetryContext;
        this.principal = principal;
        this.observation = observation;
        this.propagatedContexts = propagatedContexts;
    }

    static KoraContextSnapshot capture() {
        var propagatedContexts = new ArrayList<ThreadContextPropagator.Snapshot>(PROPAGATORS.size());
        for (var propagator : PROPAGATORS) {
            var snapshot = propagator.capture();
            if (snapshot != null) {
                propagatedContexts.add(snapshot);
            }
        }
        return new KoraContextSnapshot(
            Context.current(),
            Principal.current(),
            Observation.VALUE.isBound() ? Observation.VALUE.get() : null,
            Collections.unmodifiableList(propagatedContexts)
        );
    }

    KoraContextSnapshot fork() {
        var forks = new ArrayList<ThreadContextPropagator.Snapshot>(this.propagatedContexts.size());
        for (ThreadContextPropagator.Snapshot context : this.propagatedContexts) {
            forks.add(context.fork());
        }

        return new KoraContextSnapshot(
            this.opentelemetryContext,
            this.principal,
            this.observation,
            Collections.unmodifiableList(forks)
        );
    }

    void run(Runnable block) {
        this.call(() -> {
            block.run();
            return null;
        });
    }

    <T> T call(Supplier<T> operation) {
        var carrier = ScopedValue.where(OpentelemetryContext.VALUE, this.opentelemetryContext);
        if (this.principal != null) {
            carrier = carrier.where(Principal.VALUE, this.principal);
        }
        if (this.observation != null) {
            carrier = carrier.where(Observation.VALUE, this.observation);
        }
        Supplier<T> propagatedOperation = operation;
        for (var i = this.propagatedContexts.size() - 1; i >= 0; i--) {
            var snapshot = this.propagatedContexts.get(i);
            var nestedOperation = propagatedOperation;
            propagatedOperation = () -> snapshot.call(nestedOperation);
        }
        return carrier.call(propagatedOperation::get);
    }
}
