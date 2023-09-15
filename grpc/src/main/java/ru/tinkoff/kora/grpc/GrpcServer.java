package ru.tinkoff.kora.grpc;

import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.readiness.ReadinessProbe;
import ru.tinkoff.kora.common.readiness.ReadinessProbeFailure;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

public class GrpcServer implements Lifecycle, ReadinessProbe {

    private static final Logger logger = LoggerFactory.getLogger(GrpcServer.class);

    private final ValueOf<NettyServerBuilder> nettyServerBuilder;
    private Server server;

    private final AtomicReference<GrpcServerState> state = new AtomicReference<>(GrpcServerState.INIT);

    public GrpcServer(ValueOf<NettyServerBuilder> nettyServerBuilder) {
        this.nettyServerBuilder = nettyServerBuilder;
    }

    @Override
    public void init() throws IOException {
        logger.debug("Starting gRPC Server...");
        final long started = System.nanoTime();

        var builder = nettyServerBuilder.get();
        this.server = builder.build();
        this.server.start();
        this.state.set(GrpcServerState.RUN);
        logger.info("gRPC Server started in {}", Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
    }

    @Override
    public void release() {
        logger.debug("gRPC Server stopping...");
        final long started = System.nanoTime();

        state.set(GrpcServerState.SHUTDOWN);
        server.shutdown();

        logger.info("gRPC Server stopped in {}", Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
    }

    @Override
    public Mono<ReadinessProbeFailure> probe() {
        return switch (this.state.get()) {
            case INIT -> Mono.just(new ReadinessProbeFailure("GRPC Server init"));
            case RUN -> Mono.empty();
            case SHUTDOWN -> Mono.just(new ReadinessProbeFailure("GRPC Server shutdown"));
        };
    }

    private enum GrpcServerState {
        INIT, RUN, SHUTDOWN
    }
}
