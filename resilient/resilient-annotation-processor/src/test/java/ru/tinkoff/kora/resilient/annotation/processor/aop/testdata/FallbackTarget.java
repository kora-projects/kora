package ru.tinkoff.kora.resilient.annotation.processor.aop.testdata;

import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.resilient.fallback.annotation.Fallback;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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

    @Fallback(value = "custom_fallback2", method = "getFallbackFuture()")
    public CompletionStage<String> getValueStage() {
        if (alwaysFail)
            return CompletableFuture.failedFuture(new IllegalStateException("Failed"));

        return CompletableFuture.completedFuture(VALUE);
    }

    protected CompletableFuture<String> getFallbackStage() {
        return CompletableFuture.completedFuture(FALLBACK);
    }

    @Fallback(value = "custom_fallback2", method = "getFallbackFuture()")
    public CompletableFuture<String> getValueFuture() {
        if (alwaysFail)
            return CompletableFuture.failedFuture(new IllegalStateException("Failed"));

        return CompletableFuture.completedFuture(VALUE);
    }

    protected CompletableFuture<String> getFallbackFuture() {
        return CompletableFuture.completedFuture(FALLBACK);
    }

}
