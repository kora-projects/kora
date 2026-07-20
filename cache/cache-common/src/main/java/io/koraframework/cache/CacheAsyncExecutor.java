package io.koraframework.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;

final class CacheAsyncExecutor implements Executor {

    private static final Logger logger = LoggerFactory.getLogger(CacheAsyncExecutor.class);

    @Override
    public void execute(Runnable command) {
        Thread.ofVirtual()
            .name("kora-cache-", 0)
            .uncaughtExceptionHandler((thread, error) ->
                logger.warn("Cache asynchronous operation failed on thread {}", thread.getName(), error))
            .start(command);
    }
}
