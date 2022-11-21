package ru.tinkoff.kora.common.util;


import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Operators;
import ru.tinkoff.kora.common.Context;

import javax.annotation.Nonnull;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

public class ReactorContextHook {
    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    public static void init() {
        if (initialized.compareAndSet(false, true)) {
            Hooks.onEachOperator("ru.tinkoff.kora.ContextLifter", Operators.lift(Predicate.not(scannable -> scannable instanceof Callable<?>), (scannable, subscriber) -> {
                var ctx = subscriber.currentContext().getOrDefault(Context.class, (Context) null);
                if (ctx == null) {
                    ctx = Context.current();
                }
                return new ContextPropagator<>(subscriber, ctx);
            }));
        }
    }

    private static class ContextPropagator<T> implements CoreSubscriber<T> {
        private final CoreSubscriber<T> delegate;
        private final reactor.util.context.Context reactorContext;
        private final Context instance;

        private ContextPropagator(CoreSubscriber<T> delegate, Context current) {
            this.instance = current;
            this.delegate = delegate;
            this.reactorContext = Context.Reactor.inject(delegate.currentContext(), this.instance);
        }

        @Override
        public void onSubscribe(Subscription s) {
            var current = Context.current();
            if (current == this.instance) {
                this.delegate.onSubscribe(s);
                return;
            }
            this.instance.inject();
            try {
                this.delegate.onSubscribe(s);
            } finally {
                current.inject();
            }
        }

        @Override
        public void onNext(T o) {
            var current = Context.current();
            if (current == this.instance) {
                this.delegate.onNext(o);
                return;
            }
            this.instance.inject();
            try {
                delegate.onNext(o);
            } finally {
                current.inject();
            }
        }

        @Override
        public void onError(Throwable t) {
            var current = Context.current();
            if (current == this.instance) {
                this.delegate.onError(t);
                return;
            }
            this.instance.inject();
            try {
                delegate.onError(t);
            } finally {
                current.inject();
            }
        }

        @Override
        public void onComplete() {
            var current = Context.current();
            if (current == this.instance) {
                this.delegate.onComplete();
                return;
            }
            this.instance.inject();
            try {
                delegate.onComplete();
            } finally {
                current.inject();
            }
        }

        @Override
        @Nonnull
        public reactor.util.context.Context currentContext() {
            return this.reactorContext;
        }
    }
}
