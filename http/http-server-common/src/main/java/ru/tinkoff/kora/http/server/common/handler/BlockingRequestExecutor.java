package ru.tinkoff.kora.http.server.common.handler;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Operators;
import ru.tinkoff.kora.common.Context;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public interface BlockingRequestExecutor {
    <T> Mono<T> execute(Callable<T> handler);

    static <T> Mono<T> defaultExecute(Consumer<Runnable> executor, Callable<T> handler) {
        return Mono.create(sink -> sink.onRequest(l -> executor.accept(() -> {
            var cancelled = new AtomicBoolean(false);
            sink.onCancel(() -> cancelled.set(true));
            var reactorCtx = sink.contextView();
            Context.Reactor.current(reactorCtx).inject();
            T result;
            try {
                result = handler.call();
            } catch (Throwable e) {
                sink.error(e);
                Context.clear();
                return;
            }
            if (cancelled.get()) {
                Operators.onNextDropped(result, reactor.util.context.Context.of(reactorCtx));
                Context.clear();
                return;
            }
            try {
                sink.success(result);
            } catch (Throwable e) {
                Operators.onErrorDropped(e, reactor.util.context.Context.of(reactorCtx));
            } finally {
                Context.clear();
            }
        })));

    }

    class Default implements BlockingRequestExecutor {
        private final ExecutorService executorService;

        public Default(ExecutorService executorService) {
            this.executorService = executorService;
        }

        public final <T> Mono<T> execute(Callable<T> handler) {
            return defaultExecute(this.executorService::execute, handler);
        }
    }
}
