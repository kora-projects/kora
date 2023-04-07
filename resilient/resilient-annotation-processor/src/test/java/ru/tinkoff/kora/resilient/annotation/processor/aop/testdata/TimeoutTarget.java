package ru.tinkoff.kora.resilient.annotation.processor.aop.testdata;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.resilient.timeout.annotation.Timeout;

import java.io.IOException;
import java.time.Duration;

@Component
public class TimeoutTarget implements MockLifecycle {

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
    public Mono<String> getValueMono() {
        return Mono.fromCallable(() -> "OK")
            .delayElement(Duration.ofMillis(300));
    }

    @Timeout("custom3")
    public Flux<String> getValueFlux() {
        return Flux.from(Mono.fromCallable(() -> "OK"))
            .delayElements(Duration.ofMillis(300));
    }
}
