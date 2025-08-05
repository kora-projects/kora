package ru.tinkoff.kora.http.server.undertow;

import io.undertow.util.AttachmentKey;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.server.common.handler.BlockingRequestExecutor;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public class VirtualThreadBlockingRequestExecutor implements BlockingRequestExecutor {
    private final AttachmentKey<ExecutorService> executorServiceAttachmentKey = AttachmentKey.create(ExecutorService.class);
    private final BiFunction<String, Runnable, Thread> threadBuilder;

    @SuppressWarnings("unchecked")
    public VirtualThreadBlockingRequestExecutor() throws Throwable {
        var lookup = MethodHandles.lookup();
        var ofVirtualClass = lookup.findClass("java.lang.Thread$Builder$OfVirtual");
        var threadBuilderClass = lookup.findClass("java.lang.Thread$Builder");
        var ofVirtualMh = lookup.findStatic(Thread.class, "ofVirtual", MethodType.methodType(ofVirtualClass));

        var ofVirtual = (Supplier<Object>) LambdaMetafactory.metafactory(
            lookup,
            "get",
            MethodType.methodType(Supplier.class),
            MethodType.methodType(Object.class),
            ofVirtualMh,
            ofVirtualMh.type()
        ).getTarget().invokeExact();
        var nameMh = lookup.findVirtual(ofVirtualClass, "name", MethodType.methodType(ofVirtualClass, String.class));
        var named = (BiFunction<Object, String, Object>) LambdaMetafactory.metafactory(
            lookup,
            "apply",
            MethodType.methodType(BiFunction.class),
            MethodType.methodType(Object.class, Object.class, Object.class),
            nameMh,
            nameMh.type()
        ).getTarget().invokeExact();
        var unstartedMh = lookup.findVirtual(threadBuilderClass, "unstarted", MethodType.methodType(Thread.class, Runnable.class));
        var unstarted = (BiFunction<Object, Runnable, Thread>) LambdaMetafactory.metafactory(
            lookup,
            "apply",
            MethodType.methodType(BiFunction.class),
            MethodType.methodType(Object.class, Object.class, Object.class),
            unstartedMh,
            unstartedMh.type()
        ).getTarget().invokeExact();
        this.threadBuilder = (name, runnable) -> {
            var b = ofVirtual.get();
            b = named.apply(b, name);
            return unstarted.apply(b, runnable);
        };
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
        var executor = Executors.newSingleThreadExecutor(r -> threadBuilder.apply(threadName, r));
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
