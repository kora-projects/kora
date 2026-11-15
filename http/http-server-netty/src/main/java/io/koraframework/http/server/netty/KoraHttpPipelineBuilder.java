package io.koraframework.http.server.netty;

import io.koraframework.http.server.common.*;
import io.koraframework.http.server.netty.body.*;
import io.koraframework.http.server.netty.compression.*;
import io.koraframework.http.server.netty.handler.*;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.logging.*;
import io.netty.handler.timeout.*;
import org.jspecify.annotations.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

public final class KoraHttpPipelineBuilder {
    public static final String HANDLER_LOGGING = "logging";
    public static final String HANDLER_IDLE_STATE = "idle-state";
    public static final String HANDLER_HTTP_CODEC = "http-codec";
    public static final String HANDLER_PIPELINING = "kora-pipelining";

    private final HttpServerConfig httpServerConfig;
    private final KoraNettyServerConfig nettyConfig;
    private final RequestHandler requestHandler;
    @Nullable
    private final Compressor compressor;
    @Nullable
    private final LoggingHandler loggingHandler;

    public KoraHttpPipelineBuilder(
        HttpServerConfig httpServerConfig,
        KoraNettyServerConfig nettyConfig,
        RequestHandler requestHandler) {
        this.httpServerConfig = Objects.requireNonNull(httpServerConfig, "httpServerConfig");
        this.nettyConfig = Objects.requireNonNull(nettyConfig, "nettyConfig");
        this.requestHandler = Objects.requireNonNull(requestHandler, "requestHandler");

        var compressionStrategy = new DefaultHttpCompressionStrategy(
            nettyConfig.compressionThreshold(),
            nettyConfig.compressionLevel(),
            nettyConfig.maxZstdEncodeSize()
        );
        compressor = compressionStrategy.isEnabled() ? new Compressor(compressionStrategy) : null;
        loggingHandler = nettyConfig.wireLogLevel() != null
            ? new LoggingHandler(KoraHttpPipelineBuilder.class, nettyConfig.wireLogLevel())
            : null;
    }

    public Supplier<CompletionStage<?>> buildPipeline(Channel channel) {
        var pipeline = channel.pipeline();

        if (loggingHandler != null) {
            pipeline.addLast(HANDLER_LOGGING, loggingHandler);
        }

        long readIdleMillis = toMillisOrZero(httpServerConfig.socketReadTimeout());
        long writeIdleMillis = toMillisOrZero(httpServerConfig.socketWriteTimeout());
        long allIdleMillis = toMillisOrZero(nettyConfig.idleTimeout());
        if (readIdleMillis > 0 || writeIdleMillis > 0 || allIdleMillis > 0) {
            pipeline.addLast(HANDLER_IDLE_STATE, new IdleStateHandler(
                readIdleMillis,
                writeIdleMillis,
                allIdleMillis,
                TimeUnit.MILLISECONDS
            ));
        }

        pipeline.addLast(HANDLER_HTTP_CODEC, createServerCodec());

        var serverHandler = new PipeliningServerHandler(requestHandler);
        serverHandler.setCompressor(compressor);
        serverHandler.setBodySizeLimits(new BodySizeLimits(httpServerConfig.maxRequestBodySize().toBytes(), nettyConfig.maxRequestBufferSize()));
        serverHandler.setRequestDecompressionEnabled(nettyConfig.requestDecompressionEnabled());
        serverHandler.setHttp10KeepAlive(nettyConfig.http10KeepAlive());
        serverHandler.setRejectUnsupportedHttpVersions(nettyConfig.rejectUnsupportedHttpVersions());

        pipeline.addLast(HANDLER_PIPELINING, serverHandler);
        return new ServerShutdownSupplier(serverHandler);
    }

    private static final class ServerShutdownSupplier implements Supplier<CompletionStage<?>> {
        private final PipeliningServerHandler serverHandler;

        private ServerShutdownSupplier(PipeliningServerHandler serverHandler) {
            this.serverHandler = serverHandler;
        }

        @Override
        public CompletionStage<?> get() {
            return serverHandler.shutdownGracefully();
        }
    }

    private HttpServerCodec createServerCodec() {
        var decoderConfig = new HttpDecoderConfig()
            .setMaxInitialLineLength(nettyConfig.maxInitialLineLength())
            .setMaxHeaderSize(nettyConfig.maxHeaderSize())
            .setMaxChunkSize(nettyConfig.maxChunkSize())
            .setValidateHeaders(nettyConfig.validateHeaders())
            .setInitialBufferSize(nettyConfig.initialBufferSize())
            .setChunkedSupported(nettyConfig.chunkedSupported());
        return new HttpServerCodec(decoderConfig);
    }

    private static long toMillisOrZero(Duration duration) {
        if (duration.isNegative() || duration.isZero()) {
            return 0;
        }
        return duration.toMillis();
    }
}
