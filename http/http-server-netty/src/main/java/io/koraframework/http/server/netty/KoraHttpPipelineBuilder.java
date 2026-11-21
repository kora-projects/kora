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

    private final RequestHandler requestHandler;
    private final HttpDecoderConfig decoderConfig;
    private final BodySizeLimits bodySizeLimits;
    private final long readIdleMillis;
    private final long writeIdleMillis;
    private final long allIdleMillis;
    private final boolean requestDecompressionEnabled;
    private final boolean http10KeepAlive;
    private final boolean rejectUnsupportedHttpVersions;
    @Nullable
    private final Compressor compressor;
    @Nullable
    private final LoggingHandler loggingHandler;

    public KoraHttpPipelineBuilder(
        HttpServerConfig httpServerConfig,
        KoraNettyServerConfig nettyConfig,
        RequestHandler requestHandler) {
        Objects.requireNonNull(httpServerConfig, "httpServerConfig");
        Objects.requireNonNull(nettyConfig, "nettyConfig");
        this.requestHandler = Objects.requireNonNull(requestHandler, "requestHandler");

        var compressionStrategy = new DefaultHttpCompressionStrategy(
            nettyConfig.compressionThreshold(),
            nettyConfig.compressionLevel(),
            nettyConfig.maxZstdEncodeSize()
        );
        this.compressor = compressionStrategy.isEnabled() ? new Compressor(compressionStrategy) : null;
        this.loggingHandler = nettyConfig.wireLogLevel() != null
            ? new LoggingHandler(KoraHttpPipelineBuilder.class, nettyConfig.wireLogLevel())
            : null;

        this.decoderConfig = new HttpDecoderConfig()
            .setMaxInitialLineLength(nettyConfig.maxInitialLineLength())
            .setMaxHeaderSize(nettyConfig.maxHeaderSize())
            .setMaxChunkSize(nettyConfig.maxChunkSize())
            .setValidateHeaders(nettyConfig.validateHeaders())
            .setInitialBufferSize(nettyConfig.initialBufferSize())
            .setChunkedSupported(nettyConfig.chunkedSupported());

        this.bodySizeLimits = new BodySizeLimits(
            httpServerConfig.maxRequestBodySize().toBytes(),
            nettyConfig.maxRequestBufferSize()
        );
        this.readIdleMillis = toMillisOrZero(httpServerConfig.socketReadTimeout());
        this.writeIdleMillis = toMillisOrZero(httpServerConfig.socketWriteTimeout());
        this.allIdleMillis = toMillisOrZero(nettyConfig.idleTimeout());
        this.requestDecompressionEnabled = nettyConfig.requestDecompressionEnabled();
        this.http10KeepAlive = nettyConfig.http10KeepAlive();
        this.rejectUnsupportedHttpVersions = nettyConfig.rejectUnsupportedHttpVersions();
    }

    public Supplier<CompletionStage<?>> buildPipeline(Channel channel) {
        var pipeline = channel.pipeline();

        if (loggingHandler != null) {
            pipeline.addLast(HANDLER_LOGGING, loggingHandler);
        }

        if (readIdleMillis > 0 || writeIdleMillis > 0 || allIdleMillis > 0) {
            pipeline.addLast(HANDLER_IDLE_STATE, new IdleStateHandler(
                readIdleMillis,
                writeIdleMillis,
                allIdleMillis,
                TimeUnit.MILLISECONDS
            ));
        }

        pipeline.addLast(HANDLER_HTTP_CODEC, new HttpServerCodec(decoderConfig));

        var serverHandler = new PipeliningServerHandler(requestHandler);
        serverHandler.setCompressor(compressor);
        serverHandler.setBodySizeLimits(bodySizeLimits);
        serverHandler.setRequestDecompressionEnabled(requestDecompressionEnabled);
        serverHandler.setHttp10KeepAlive(http10KeepAlive);
        serverHandler.setRejectUnsupportedHttpVersions(rejectUnsupportedHttpVersions);

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

    private static long toMillisOrZero(Duration duration) {
        if (duration.isNegative() || duration.isZero()) {
            return 0;
        }
        return duration.toMillis();
    }
}
