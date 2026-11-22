package io.koraframework.http.server.netty;

import io.koraframework.config.common.annotation.*;

@ConfigMapper
public interface KoraSystemNettyServerConfig extends KoraNettyServerConfig {

    @Override
    default int soBacklog() {
        return 128;
    }

    @Override
    default int writeBufferWaterMarkLow() {
        return 16 * 1024;
    }

    @Override
    default int writeBufferWaterMarkHigh() {
        return 64 * 1024;
    }

    @Override
    default int recvBufMin() {
        return 256;
    }

    @Override
    default int recvBufInitial() {
        return 512;
    }

    @Override
    default int recvBufMax() {
        return 4096;
    }

    @Override
    default int maxMessagesPerRead() {
        return 8;
    }

    @Override
    default int maxChunkSize() {
        return 8192;
    }

    @Override
    default int initialBufferSize() {
        return 256;
    }
}
