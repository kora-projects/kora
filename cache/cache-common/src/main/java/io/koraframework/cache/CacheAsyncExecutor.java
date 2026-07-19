package io.koraframework.cache;

import java.util.concurrent.Executor;

public final class CacheAsyncExecutor implements Executor {

    @Override
    public void execute(Runnable command) {
        Thread.ofVirtual()
            .name("kora-cache-", 0)
            .start(command);
    }
}
