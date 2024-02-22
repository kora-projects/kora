package ru.tinkoff.kora.http.server.undertow;

import io.undertow.Undertow;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.XnioWorker;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.util.TimeUtils;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;
import ru.tinkoff.kora.http.server.common.PrivateHttpServer;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;

import java.net.InetSocketAddress;

public class UndertowPrivateHttpServer implements PrivateHttpServer {

    private static final Logger logger = LoggerFactory.getLogger(UndertowPrivateHttpServer.class);

    private final ValueOf<HttpServerConfig> config;
    private final ValueOf<UndertowPrivateApiHandler> privateApiHandler;
    private final XnioWorker xnioWorker;
    private volatile Undertow undertow;

    public UndertowPrivateHttpServer(ValueOf<HttpServerConfig> config, ValueOf<UndertowPrivateApiHandler> privateApiHandler, @Nullable XnioWorker xnioWorker) {
        this.config = config;
        this.xnioWorker = xnioWorker;
        this.privateApiHandler = privateApiHandler;
    }

    @Override
    public void release() {
        if (this.undertow != null) {
            logger.debug("Private HTTP Server (Undertow) stopping...");
            try {
                Thread.sleep(this.config.get().shutdownWait().toMillis());
            } catch (InterruptedException e) {
                // ignore
            }
            final long started = TimeUtils.started();
            this.undertow.stop();
            this.undertow = null;
            logger.info("Private HTTP Server (Undertow) stopped in {}", TimeUtils.tookForLogging(started));
        }
    }

    @Override
    public void init() {
        logger.debug("Private HTTP Server (Undertow) starting...");
        final long started = TimeUtils.started();
        this.undertow = this.createServer();
        this.undertow.start();
        var data = StructuredArgument.marker("port", this.port());
        logger.info(data, "Private HTTP Server (Undertow) started in {}", TimeUtils.tookForLogging(started));
    }

    private Undertow createServer() {
        return Undertow.builder()
            .addHttpListener(this.config.get().privateApiHttpPort(), "0.0.0.0", exchange -> this.privateApiHandler.get().handleRequest(exchange))
            .setWorker(this.xnioWorker)
            .build();
    }

    @Override
    public int port() {
        if (this.undertow == null) {
            return -1;
        }
        var info = this.undertow.getListenerInfo().get(0);
        var address = (InetSocketAddress) info.getAddress();
        return address.getPort();
    }
}
