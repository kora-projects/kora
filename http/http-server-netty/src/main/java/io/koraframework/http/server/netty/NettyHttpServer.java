package io.koraframework.http.server.netty;

import io.koraframework.application.graph.*;
import io.koraframework.common.*;
import io.koraframework.common.readiness.*;
import io.koraframework.common.util.*;
import io.koraframework.http.server.common.*;
import io.koraframework.http.server.netty.NettySystemHttpServerModule.*;
import io.koraframework.http.server.netty.handler.*;
import io.netty.bootstrap.*;
import io.netty.channel.*;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.*;
import org.jspecify.annotations.*;
import org.slf4j.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

public final class NettyHttpServer implements HttpServer, ReadinessProbe, Lifecycle {

    private static final Logger log = LoggerFactory.getLogger(NettyHttpServer.class);
    private static final int MAX_RANDOM_PORT_BIND_ATTEMPTS = 10;
    private static final boolean CHECK_LOOPBACK_PORT_SHADOWING = PlatformDependent.isOsx()
        || System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("bsd");

    private final AtomicReference<HttpServerState> state = new AtomicReference<>(HttpServerState.INIT);
    private final Set<Supplier<CompletionStage<?>>> activeConnections = ConcurrentHashMap.newKeySet();

    private final String name;
    private final NettyResources resources;
    private final ValueOf<HttpServerConfig> httpServerConfig;
    private final ValueOf<KoraNettyServerConfig> nettyServerConfig;
    private final ValueOf<RequestHandler> requestHandler;
    private final @Nullable Configurer<ServerBootstrap> configurer;
    private volatile @Nullable Channel serverChannel;

    public NettyHttpServer(
        String name,
        NettyResources resources,
        ValueOf<HttpServerConfig> httpServerConfig,
        ValueOf<KoraNettyServerConfig> nettyServerConfig,
        ValueOf<RequestHandler> requestHandler,
        @Nullable Configurer<ServerBootstrap> configurer
    ) {
        this.name = Objects.requireNonNull(name, "name");
        this.resources = Objects.requireNonNull(resources, "resources");
        this.httpServerConfig = Objects.requireNonNull(httpServerConfig, "httpServerConfig");
        this.nettyServerConfig = Objects.requireNonNull(nettyServerConfig, "nettyServerConfig");
        this.requestHandler = Objects.requireNonNull(requestHandler, "requestHandler");
        this.configurer = configurer;
    }

    @Override
    public void init() {
        try {
            log.debug("HTTP Server {} (Netty) starting...", name);
            long started = TimeUtils.started();

            var config = httpServerConfig.get();
            var nettyConfig = nettyServerConfig.get();
            var pipelineBuilder = new KoraHttpPipelineBuilder(config, nettyConfig, requestHandler.get());
            var bootstrap = new ServerBootstrap()
                .group(resources.bossGroup(), resources.workerGroup())
                .channelFactory(resources.serverChannelFactory());

            if (configurer != null) {
                bootstrap = configurer.configure(bootstrap.clone());
            }

            bootstrap.childHandler(new HttpServerChannelInitializer(pipelineBuilder));

            int port = config.port();
            var host = nettyConfig.host();
            state.set(HttpServerState.RUN);
            ChannelFuture bindFuture;
            if (host == null || host.isBlank()) {
                bindFuture = port == 0 && CHECK_LOOPBACK_PORT_SHADOWING
                    ? bindRandomWildcardPort(bootstrap)
                    : bootstrap.bind(port).awaitUninterruptibly();
            } else {
                bindFuture = bootstrap.bind(host, port).awaitUninterruptibly();
            }

            if (!bindFuture.isSuccess()) {
                throw bindFuture.cause();
            }
            serverChannel = bindFuture.channel();
            var boundPort = port();
            log.info("HTTP server '{}' (Netty) started on port {} in {}", name, boundPort, TimeUtils.tookForLogging(started));
        } catch (Throwable e) {
            if (e instanceof BindException || e.getCause() instanceof BindException) {
                throw new IllegalStateException("HTTP server '%s' (Netty) failed to start on port '%s': port is already in use; stop the other process or configure a different port".formatted(name, httpServerConfig.get().port()), e);
            } else {
                throw new IllegalStateException("HTTP server '%s' (Netty) failed to start on port '%s': %s; check server config, handler initialization, and network binding".formatted(name, httpServerConfig.get().port(), e.getMessage()), e);
            }
        }
    }

    private final class HttpServerChannelInitializer extends ChannelInitializer<Channel> {
        private final KoraHttpPipelineBuilder pipelineBuilder;

        private HttpServerChannelInitializer(KoraHttpPipelineBuilder pipelineBuilder) {
            this.pipelineBuilder = pipelineBuilder;
        }

        @Override
        protected void initChannel(Channel ch) {
            if (state.get() == HttpServerState.SHUTDOWN) {
                ch.close();
                return;
            }
            var shutdownHook = pipelineBuilder.buildPipeline(ch);
            activeConnections.add(shutdownHook);
            ch.closeFuture().addListener(new RemoveConnectionListener(shutdownHook));
        }
    }

    private final class RemoveConnectionListener implements ChannelFutureListener {
        private final Supplier<CompletionStage<?>> shutdownHook;

        private RemoveConnectionListener(Supplier<CompletionStage<?>> shutdownHook) {
            this.shutdownHook = shutdownHook;
        }

        @Override
        public void operationComplete(ChannelFuture future) {
            activeConnections.remove(shutdownHook);
        }
    }

    private static ChannelFuture bindRandomWildcardPort(ServerBootstrap bootstrap) {
        for (int attempt = 1; ; attempt++) {
            var future = bootstrap.bind(0).awaitUninterruptibly();
            if (!future.isSuccess()
                || attempt >= MAX_RANDOM_PORT_BIND_ATTEMPTS
                || !(future.channel().localAddress() instanceof InetSocketAddress address)
                || !isLoopbackPortTaken(address.getPort())) {
                return future;
            }
            log.debug("Random port {} is already used on loopback address, binding again", address.getPort());
            future.channel().close().awaitUninterruptibly();
        }
    }

    private static boolean isLoopbackPortTaken(int port) {
        try (var ss = new ServerSocket()) {
            ss.setReuseAddress(false);
            ss.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    @Override
    public void release() {
        log.debug("HTTP Server {} (Netty) stopping...", name);
        state.set(HttpServerState.SHUTDOWN);
        long started = TimeUtils.started();
        long shutdownWaitMillis = httpServerConfig.get().shutdownWait().toMillis();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(shutdownWaitMillis);

        var localChannel = serverChannel;
        serverChannel = null;
        if (localChannel != null) {
            try {
                localChannel.close().await(shutdownWaitMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        drainWorkerRegistrationTasks(deadline);

        if (!activeConnections.isEmpty()) {
            try {
                log.debug("HTTP Server {} (Netty) awaiting graceful shutdown for {} active connections...", name, activeConnections.size());
                var futuresList = new ArrayList<CompletableFuture<?>>(activeConnections.size());
                for (var hook : activeConnections) {
                    futuresList.add(hook.get().toCompletableFuture());
                }
                var futures = futuresList.toArray(new CompletableFuture[0]);
                long remainingMillis = Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
                CompletableFuture.allOf(futures).get(remainingMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("HTTP Server {} (Netty) graceful shutdown interrupted", name, e);
            } catch (TimeoutException e) {
                log.warn("HTTP Server {} (Netty) graceful shutdown timed out after {}ms", name, shutdownWaitMillis);
            } catch (Throwable e) {
                log.warn("HTTP Server {} (Netty) graceful shutdown failed", name, e);
            }
        }

        log.info("HTTP Server {} (Netty) stopped in {}", name, TimeUtils.tookForLogging(started));
    }

    private void drainWorkerRegistrationTasks(long deadlineNanos) {
        var roundTrips = new ArrayList<Future<?>>();
        for (var loop : resources.workerGroup()) {
            if (!loop.inEventLoop() && !loop.isShuttingDown()) {
                roundTrips.add(loop.submit(NoopRunnable.INSTANCE));
            }
        }
        for (var roundTrip : roundTrips) {
            long remaining = deadlineNanos - System.nanoTime();
            if (remaining <= 0) {
                break;
            }
            roundTrip.awaitUninterruptibly(remaining, TimeUnit.NANOSECONDS);
        }
    }

    private static final class NoopRunnable implements Runnable {
        private static final NoopRunnable INSTANCE = new NoopRunnable();

        private NoopRunnable() {
        }

        @Override
        public void run() {
        }
    }

    @Override
    public int port() {
        var localChannel = serverChannel;
        if (localChannel == null) {
            return -1;
        }
        var address = localChannel.localAddress();
        if (address instanceof InetSocketAddress inet) {
            return inet.getPort();
        }
        return -1;
    }

    @Nullable
    @Override
    public ReadinessProbeFailure probe() {
        var currentState = state.get();
        if (currentState == HttpServerState.INIT) {
            return new ReadinessProbeFailure("HTTP Server " + name + " (Netty) init");
        }
        if (currentState == HttpServerState.RUN) {
            return null;
        }
        if (currentState == HttpServerState.SHUTDOWN) {
            return new ReadinessProbeFailure("HTTP Server " + name + " (Netty) shutdown");
        }
        return new ReadinessProbeFailure("HTTP Server " + name + " (Netty) unknown state");
    }

    private enum HttpServerState {
        INIT, RUN, SHUTDOWN
    }
}
