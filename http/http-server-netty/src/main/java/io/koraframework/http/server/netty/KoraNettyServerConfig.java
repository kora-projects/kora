package io.koraframework.http.server.netty;

import io.koraframework.config.common.annotation.*;
import io.netty.handler.logging.*;
import org.jspecify.annotations.*;

import java.time.*;

@ConfigMapper
public interface KoraNettyServerConfig {

    @Nullable
    String host();

    default int soBacklog() {
        return 8192;
    }

    default boolean tcpNoDelay() {
        return true;
    }

    default int writeBufferWaterMarkLow() {
        return 32 * 1024;
    }

    default int writeBufferWaterMarkHigh() {
        return 64 * 1024;
    }

    default int recvBufMin() {
        return 256;
    }

    default int recvBufInitial() {
        return 512;
    }

    default int recvBufMax() {
        return 32768;
    }

    default int maxMessagesPerRead() {
        return 16;
    }

    default int maxInitialLineLength() {
        return 4096;
    }

    default int maxHeaderSize() {
        return 8192;
    }

    default int maxChunkSize() {
        return 32768;
    }

    default int initialBufferSize() {
        return 256;
    }

    default boolean validateHeaders() {
        return false;
    }

    default boolean chunkedSupported() {
        return true;
    }

    default long maxRequestBufferSize() {
        return Long.MAX_VALUE;
    }

    default boolean requestDecompressionEnabled() {
        return true;
    }

    default int compressionThreshold() {
        return -1;
    }

    default int compressionLevel() {
        return 6;
    }

    default int maxZstdEncodeSize() {
        return 32 * 1024 * 1024;
    }

    default boolean http10KeepAlive() {
        return true;
    }

    default boolean rejectUnsupportedHttpVersions() {
        return true;
    }

    default Duration idleTimeout() {
        return Duration.ofSeconds(60);
    }

    default int directToHeapThreshold() {
        return 0;
    }

    @Nullable
    LogLevel wireLogLevel();
}
