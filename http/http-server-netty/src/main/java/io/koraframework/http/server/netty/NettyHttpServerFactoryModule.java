package io.koraframework.http.server.netty;

import io.koraframework.application.graph.*;
import io.koraframework.common.*;
import io.koraframework.common.annotation.*;
import io.koraframework.config.common.*;
import io.koraframework.config.common.mapper.*;
import io.koraframework.http.server.common.*;
import io.koraframework.http.server.common.router.*;
import io.koraframework.http.server.common.telemetry.*;
import io.koraframework.http.server.netty.NettySystemHttpServerModule.*;
import io.koraframework.http.server.netty.handler.*;
import io.netty.bootstrap.*;
import io.netty.buffer.*;
import io.netty.channel.*;
import org.jspecify.annotations.*;

import java.util.concurrent.*;

public class NettyHttpServerFactoryModule extends HttpServerFactoryModule {

    private final String name;
    private final ByteBufAllocator allocator;

    public NettyHttpServerFactoryModule(String name, String configPath, ByteBufAllocator allocator) {
        super(configPath);
        this.name = name;
        this.allocator = allocator;
    }

    @DefaultComponent
    @Tag(Tag.Factory.class)
    public KoraNettyServerConfig koraNettyServerConfig(Config config, ConfigValueMapper<KoraNettyServerConfig> mapper) {
        return mapper.mapOrThrow(config.get(configPath));
    }

    @Root
    @Tag(Tag.Factory.class)
    public NettyHttpServer server(
        NettyResources resources,
        @Tag(Tag.Factory.class) ValueOf<HttpServerConfig> httpServerConfig,
        @Tag(Tag.Factory.class) ValueOf<KoraNettyServerConfig> nettyServerConfig,
        @Tag(Tag.Factory.class) ValueOf<RequestHandler> requestHandler,
        @Tag(Tag.Factory.class) @Nullable Configurer<ServerBootstrap> configurer
    ) {
        return new NettyHttpServer(
            name,
            resources,
            httpServerConfig,
            nettyServerConfig,
            requestHandler,
            configurer
        );
    }

    @DefaultComponent
    @Tag(Tag.Factory.class)
    public Configurer<ServerBootstrap> defaultNettyBootstrapConfigurer(
        @Tag(Tag.Factory.class) ValueOf<HttpServerConfig> httpServerConfig,
        @Tag(Tag.Factory.class) ValueOf<KoraNettyServerConfig> nettyServerConfig
    ) {
        return new DefaultNettyBootstrapConfigurer(httpServerConfig, nettyServerConfig, allocator);
    }

    private static final class DefaultNettyBootstrapConfigurer implements Configurer<ServerBootstrap> {
        private final ValueOf<HttpServerConfig> httpServerConfig;
        private final ValueOf<KoraNettyServerConfig> nettyServerConfig;
        private final ByteBufAllocator allocator;

        private DefaultNettyBootstrapConfigurer(
            ValueOf<HttpServerConfig> httpServerConfig,
            ValueOf<KoraNettyServerConfig> nettyServerConfig,
            ByteBufAllocator allocator
        ) {
            this.httpServerConfig = httpServerConfig;
            this.nettyServerConfig = nettyServerConfig;
            this.allocator = allocator;
        }

        @Override
        public ServerBootstrap configure(ServerBootstrap bootstrap) {
            var config = httpServerConfig.get();
            var nettyConfig = nettyServerConfig.get();

            var recvAllocator = new KoraRecvByteBufAllocator(
                nettyConfig.recvBufMin(),
                nettyConfig.recvBufInitial(),
                nettyConfig.recvBufMax(),
                nettyConfig.maxMessagesPerRead()
            );

            var waterMark = new WriteBufferWaterMark(
                nettyConfig.writeBufferWaterMarkLow(),
                nettyConfig.writeBufferWaterMarkHigh()
            );

            return bootstrap
                .option(ChannelOption.ALLOCATOR, allocator)
                .option(ChannelOption.SO_BACKLOG, nettyConfig.soBacklog())
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.ALLOCATOR, allocator)
                .childOption(ChannelOption.SO_KEEPALIVE, config.socketKeepAliveEnabled())
                .childOption(ChannelOption.TCP_NODELAY, nettyConfig.tcpNoDelay())
                .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, waterMark)
                .childOption(ChannelOption.RECVBUF_ALLOCATOR, recvAllocator);
        }
    }

    @DefaultComponent
    @Tag(Tag.Factory.class)
    public RequestHandler koraRequestHandler(
        @Tag(Tag.Factory.class) ValueOf<HttpServerConfig> httpServerConfig,
        @Tag(Tag.Factory.class) ValueOf<KoraNettyServerConfig> nettyServerConfig,
        @Tag(Tag.Factory.class) HttpServerRouter httpServerRouter,
        HttpServerTelemetryFactory telemetryFactory,
        ThreadFactory koraVirtualThreadFactory
    ) {
        var config = httpServerConfig.get();
        var telemetry = telemetryFactory.get(name, config.port(), config.telemetry());
        return new KoraRequestHandler(
            httpServerConfig,
            nettyServerConfig,
            config.telemetry().logging().enabled(),
            httpServerRouter,
            telemetry,
            koraVirtualThreadFactory
        );
    }
}
