package io.koraframework.http.server.netty;

import io.koraframework.http.server.common.*;
import io.koraframework.http.server.netty.body.*;
import io.koraframework.http.server.netty.compression.*;
import io.koraframework.http.server.netty.handler.*;
import io.koraframework.http.server.netty.request.*;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.logging.*;
import io.netty.handler.timeout.*;
import org.jspecify.annotations.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

import static io.koraframework.http.server.netty.handler.KoraRequestHandler.*;

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
        compressor = compressionStrategy.isEnabled() ? new Compressor(compressionStrategy) : null;
        loggingHandler = nettyConfig.wireLogLevel() != null
            ? new LoggingHandler(KoraHttpPipelineBuilder.class, nettyConfig.wireLogLevel())
            : null;

        decoderConfig = new HttpDecoderConfig()
            .setMaxInitialLineLength(nettyConfig.maxInitialLineLength())
            .setMaxHeaderSize(nettyConfig.maxHeaderSize())
            .setMaxChunkSize(nettyConfig.maxChunkSize())
            .setValidateHeaders(nettyConfig.validateHeaders())
            .setInitialBufferSize(nettyConfig.initialBufferSize())
            .setChunkedSupported(nettyConfig.chunkedSupported())
            .setHeadersFactory(REQUEST_HEADERS_FACTORY)
            .setTrailersFactory(LAZY_TRAILERS_FACTORY);

        bodySizeLimits = new BodySizeLimits(
            httpServerConfig.maxRequestBodySize().toBytes(),
            nettyConfig.maxRequestBufferSize()
        );
        readIdleMillis = toMillisOrZero(httpServerConfig.socketReadTimeout());
        writeIdleMillis = toMillisOrZero(httpServerConfig.socketWriteTimeout());
        allIdleMillis = toMillisOrZero(nettyConfig.idleTimeout());
        requestDecompressionEnabled = nettyConfig.requestDecompressionEnabled();
        http10KeepAlive = nettyConfig.http10KeepAlive();
        rejectUnsupportedHttpVersions = nettyConfig.rejectUnsupportedHttpVersions();
    }

    public Supplier<CompletionStage<?>> buildPipeline(Channel channel) {
        var pipeline = channel.pipeline();

        if (loggingHandler != null) {
            pipeline.addLast(HANDLER_LOGGING, loggingHandler);
        }

        long effectiveReadIdleMillis = readIdleMillis > 0 ? readIdleMillis : allIdleMillis;
        if (effectiveReadIdleMillis > 0 || writeIdleMillis > 0) {
            pipeline.addLast(HANDLER_IDLE_STATE, new IdleStateHandler(
                effectiveReadIdleMillis,
                writeIdleMillis,
                0,
                TimeUnit.MILLISECONDS
            ));
        }

        pipeline.addLast(HANDLER_HTTP_CODEC, new KoraHttpServerCodec(decoderConfig));

        var serverHandler = new PipeliningServerHandler(requestHandler);
        serverHandler.setCompressor(compressor);
        serverHandler.setBodySizeLimits(bodySizeLimits);
        serverHandler.setRequestDecompressionEnabled(requestDecompressionEnabled);
        serverHandler.setHttp10KeepAlive(http10KeepAlive);
        serverHandler.setRejectUnsupportedHttpVersions(rejectUnsupportedHttpVersions);

        pipeline.addLast(HANDLER_PIPELINING, serverHandler);
        return new ServerShutdownSupplier(serverHandler);
    }

    private static final HttpHeadersFactory LAZY_TRAILERS_FACTORY = new HttpHeadersFactory() {
        @Override
        public HttpHeaders newHeaders() {
            return new LazyTrailingHeaders();
        }

        @Override
        public HttpHeaders newEmptyHeaders() {
            return EmptyHttpHeaders.INSTANCE;
        }
    };

    private static final class LazyTrailingHeaders extends EmptyHttpHeaders {
        @Nullable
        private HttpHeaders delegate;

        private HttpHeaders ensureDelegate() {
            var d = delegate;
            if (d == null) {
                d = DefaultHttpHeadersFactory.trailersFactory().withValidation(false).newHeaders();
                delegate = d;
            }
            return d;
        }

        @Override
        public HttpHeaders add(CharSequence name, Object value) {
            return ensureDelegate().add(name, value);
        }

        @Override
        public HttpHeaders add(String name, Object value) {
            return ensureDelegate().add(name, value);
        }

        @Override
        public HttpHeaders set(CharSequence name, Object value) {
            return ensureDelegate().set(name, value);
        }

        @Override
        public HttpHeaders set(String name, Object value) {
            return ensureDelegate().set(name, value);
        }

        @Override
        public boolean isEmpty() {
            return delegate == null || delegate.isEmpty();
        }

        @Override
        public int size() {
            return delegate == null ? 0 : delegate.size();
        }

        @Override
        public @Nullable String get(CharSequence name) {
            return delegate == null ? null : delegate.get(name);
        }

        @Override
        public @Nullable String get(String name) {
            return delegate == null ? null : delegate.get(name);
        }

        @Override
        public Iterator<Map.Entry<String, String>> iterator() {
            return delegate == null ? Collections.emptyIterator() : delegate.iterator();
        }

        @Override
        public Iterator<Map.Entry<CharSequence, CharSequence>> iteratorCharSequence() {
            return delegate == null ? Collections.emptyIterator() : delegate.iteratorCharSequence();
        }
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
