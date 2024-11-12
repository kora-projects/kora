package ru.tinkoff.kora.grpc.server;

import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.readiness.ReadinessProbe;
import ru.tinkoff.kora.common.readiness.ReadinessProbeFailure;
import ru.tinkoff.kora.common.util.TimeUtils;
import ru.tinkoff.kora.grpc.server.config.GrpcServerConfig;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class GrpcNettyServer implements Lifecycle, ReadinessProbe {

    private static final Logger logger = LoggerFactory.getLogger(GrpcNettyServer.class);

    private final ValueOf<NettyServerBuilder> nettyServerBuilder;
    private Server server;

    private final AtomicReference<GrpcServerState> state = new AtomicReference<>(GrpcServerState.INIT);
    private final ValueOf<GrpcServerConfig> config;

    public GrpcNettyServer(ValueOf<NettyServerBuilder> nettyServerBuilder,
                           ValueOf<GrpcServerConfig> config) {
        this.nettyServerBuilder = nettyServerBuilder;
        this.config = config;
    }

    @Override
    public void init() throws IOException {
        logger.debug("Starting gRPC Server...");
        final long started = TimeUtils.started();

        var builder = nettyServerBuilder.get();
        this.server = builder.build();
        this.server.start();
        this.state.set(GrpcServerState.RUN);
        logger.info("gRPC Server started in {}", TimeUtils.tookForLogging(started));
    }

    @Override
    public void release() {
        logger.debug("gRPC Server stopping...");
        final long started = TimeUtils.started();

        state.set(GrpcServerState.SHUTDOWN);
        server.shutdown();
        final Duration shutdownAwait = config.get().shutdownWait();
        try {
            logger.debug("gRPC Server awaiting graceful shutdown...");
            if (!server.awaitTermination(shutdownAwait.toMillis(), TimeUnit.MILLISECONDS)) {
                logger.warn("gRPC Server failed completing graceful shutdown in {}", shutdownAwait);
            }
        } catch (InterruptedException e) {
            logger.warn("gRPC Server failed completing graceful shutdown in {}", shutdownAwait);
            e.printStackTrace();
        }

        logger.info("gRPC Server stopped in {}", TimeUtils.tookForLogging(started));
    }

    @Override
    public ReadinessProbeFailure probe() {
        return switch (this.state.get()) {
            case INIT -> new ReadinessProbeFailure("GRPC Server init");
            case RUN -> null;
            case SHUTDOWN -> new ReadinessProbeFailure("GRPC Server shutdown");
        };
    }

    private enum GrpcServerState {
        INIT, RUN, SHUTDOWN
    }
}
