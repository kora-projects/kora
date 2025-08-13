package ru.tinkoff.kora.http.server.undertow;

import io.undertow.util.AttachmentKey;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.common.util.VirtualThreadFactory;
import ru.tinkoff.kora.http.server.common.handler.BlockingRequestExecutor;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VirtualThreadBlockingRequestExecutor implements BlockingRequestExecutor {

    private final AttachmentKey<ExecutorService> executorServiceAttachmentKey = AttachmentKey.create(ExecutorService.class);
    private final VirtualThreadFactory threadFactory;

    public VirtualThreadBlockingRequestExecutor() throws IllegalStateException{
        this.threadFactory = new VirtualThreadFactory();
    }

    @Override
    public <T> CompletionStage<T> execute(Context context, Callable<T> handler) {
        var exchange = Objects.requireNonNull(UndertowContext.get(context));
        var connection = exchange.getConnection();
        var existingExecutor = connection.getAttachment(executorServiceAttachmentKey);
        if (existingExecutor != null) {
            return BlockingRequestExecutor.defaultExecute(context, existingExecutor::submit, handler);
        }
        var threadName = "kora-undertow-" + connection.getId();
        var executor = Executors.newSingleThreadExecutor(r -> threadFactory.newThread(threadName, r));
        connection.addCloseListener(c -> {
            var e = c.removeAttachment(executorServiceAttachmentKey);
            if (e != null) {
                e.shutdownNow();
            }
        });
        connection.putAttachment(executorServiceAttachmentKey, executor);
        return BlockingRequestExecutor.defaultExecute(context, executor::submit, handler);
    }
}
