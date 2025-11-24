package ru.tinkoff.kora.resilient.annotation.processor.aop.testdata;

import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.resilient.timeout.annotation.Timeout;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@Root
public class TimeoutTarget {

    @Timeout("custom1")
    public String getValueSync() {
        try {
            Thread.sleep(300);
            return "OK";
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Timeout("custom1")
    public void getValueSyncVoid() {
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Timeout("custom1")
    public String getValueSyncCheckedException() throws IOException {
        try {
            Thread.sleep(300);
            return "OK";
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Timeout("custom1")
    public void getValueSyncCheckedExceptionVoid() throws IOException {
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Timeout("custom1")
    public void getValueSyncCheckedExceptionVoidFailed() throws IOException {
        throw new IOException("OPS");
    }

    @Timeout("custom2")
    public CompletionStage<String> getValueStage() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(300);
                return "OK";
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @Timeout("custom3")
    public CompletableFuture<String> getValueFuture() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(300);
                return "OK";
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        });
    }
}
