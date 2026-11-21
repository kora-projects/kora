package io.koraframework.http.server.netty;

import io.koraframework.config.common.annotation.*;
import io.netty.handler.logging.*;
import org.jspecify.annotations.*;

import java.time.*;

@ConfigMapper
public interface KoraNettyServerConfig {

    @Nullable
    String host();

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
        return 1024;
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
        return 1024;
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

    @Nullable
    LogLevel wireLogLevel();
}
