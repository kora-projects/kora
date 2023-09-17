package ru.tinkoff.kora.http.server.common.handler;

import ru.tinkoff.kora.common.Context;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Consumer;

public interface BlockingRequestExecutor {
    <T> CompletionStage<T> execute(Context context, Callable<T> handler);

    static <T> CompletionStage<T> defaultExecute(Context context, Consumer<Runnable> executor, Callable<T> handler) {
        var future = new CompletableFuture<T>();
        executor.accept(() -> {
            var oldCtx = Context.current();
            context.inject();
            try {
                T result;
                try {
                    result = handler.call();
                } catch (CompletionException e) {
                    future.completeExceptionally(e.getCause());
                    return;
                } catch (ExecutionException e) {
                    future.completeExceptionally(Objects.requireNonNullElse(e.getCause(), e));
                    return;
                } catch (Throwable e) {
                    future.completeExceptionally(e);
                    return;
                }
                if (future.isCancelled()) {
                    return;
                }
                future.complete(result);
            } finally {
                oldCtx.inject();
            }
        });
        return future;
    }

    class Default implements BlockingRequestExecutor {
        private final ExecutorService executorService;

        public Default(ExecutorService executorService) {
            this.executorService = executorService;
        }

        @Override
        public final <T> CompletionStage<T> execute(Context context, Callable<T> handler) {
            return defaultExecute(context, this.executorService::execute, handler);
        }
    }
}
