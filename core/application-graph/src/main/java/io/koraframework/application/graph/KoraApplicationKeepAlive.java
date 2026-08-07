package io.koraframework.application.graph;

import java.util.concurrent.Semaphore;

/**
 * Holds the JVM alive for as long as an initialized application is running.
 *
 * <p>Kora runs on virtual threads, and virtual threads are always daemon, so once
 * {@link KoraApplication#run} returns there is nothing left to keep the process up and it exits with
 * code 0 right after start. Individual server modules used to each own a non-daemon thread for this;
 * doing it once here means a module that owns no such thread cannot take the application down with it.
 */
final class KoraApplicationKeepAlive {

    private final Semaphore semaphore = new Semaphore(0);
    private final Thread thread;

    private KoraApplicationKeepAlive() {
        this.thread = new Thread(this.semaphore::acquireUninterruptibly);
        this.thread.setName("kora-app-keep-alive");
        this.thread.setDaemon(false);
    }

    static KoraApplicationKeepAlive start() {
        var keepAlive = new KoraApplicationKeepAlive();
        keepAlive.thread.start();
        return keepAlive;
    }

    void stop() {
        this.semaphore.release();
    }
}
