package ru.tinkoff.kora.resilient.annotation.processor.aop.testdata;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.resilient.fallback.annotation.Fallback;

import java.io.IOException;

@Component
@Root
public class FallbackTarget {

    public static final String VALUE = "OK";
    public static final String FALLBACK = "FALLBACK";

    public boolean alwaysFail = true;

    @Fallback(value = "custom_fallback1", method = "getFallbackSync()")
    public String getValueSync() {
        if (alwaysFail)
            throw new IllegalStateException("Failed");

        return VALUE;
    }

    protected String getFallbackSync() {
        return FALLBACK;
    }

    @Fallback(value = "custom_fallback1", method = "getFallbackSyncVoid()")
    public void getValueSyncVoid() {
        if (alwaysFail)
            throw new IllegalStateException("Failed");
    }

    protected void getFallbackSyncVoid() {

    }

    @Fallback(value = "custom_fallback1", method = "getFallbackSyncCheckedException()")
    public String getValueSyncCheckedException() throws IOException {
        if (alwaysFail)
            throw new IllegalStateException("Failed");

        return VALUE;
    }

    protected String getFallbackSyncCheckedException() throws IOException {
        return FALLBACK;
    }

    @Fallback(value = "custom_fallback1", method = "getFallbackSyncCheckedExceptionVoid()")
    public void getValueSyncCheckedExceptionVoid() throws IOException {
        if (alwaysFail)
            throw new IllegalStateException("Failed");
    }

    protected void getFallbackSyncCheckedExceptionVoid() throws IOException {

    }

    @Fallback(value = "custom_fallback2", method = "getFallbackMono()")
    public Mono<String> getValueMono() {
        if (alwaysFail)
            return Mono.error(new IllegalStateException("Failed"));

        return Mono.just(VALUE);
    }

    protected Mono<String> getFallbackMono() {
        return Mono.just(FALLBACK);
    }

    @Fallback(value = "custom_fallback3", method = "getFallbackFlux()")
    public Flux<String> getValueFlux() {
        if (alwaysFail)
            return Flux.error(new IllegalStateException("Failed"));

        return Flux.just(VALUE);
    }

    protected Flux<String> getFallbackFlux() {
        return Flux.just(FALLBACK);
    }
}
