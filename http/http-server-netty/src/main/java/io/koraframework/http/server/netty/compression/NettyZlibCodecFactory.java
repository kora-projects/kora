/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https:*
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.koraframework.http.server.netty.compression;

import io.netty.buffer.*;
import io.netty.channel.*;
import io.netty.handler.codec.compression.*;
import io.netty.handler.codec.http.websocketx.extensions.*;
import io.netty.handler.codec.http.websocketx.extensions.compression.*;
import org.jspecify.annotations.*;

public interface NettyZlibCodecFactory {
    ZlibEncoder newZlibEncoder(ZlibWrapper wrapper, int compressionLevel, int windowBits, int memLevel);

    ZlibDecoder newZlibDecoder(ZlibWrapper wrapper, int maxAllocation);

    default @Nullable ByteBuf compressZlib(ZlibWrapper wrapper, int compressionLevel, int windowBits, int memLevel,
                                           ByteBuf body, ByteBufAllocator allocator) {
        return null;
    }

    default WebSocketServerExtensionHandler newWebSocketServerCompressionHandler(int maxMessageBytes) {
        return new WebSocketServerCompressionHandler(0);
    }

    default WebSocketClientExtensionHandler newWebSocketClientCompressionHandler(int maxMessageBytes) {
        return SharedWebSocketHandlers.CLIENT_COMPRESSION;
    }

    default boolean isBrotliAvailable() {
        return Brotli.isAvailable();
    }

    default boolean isZstdAvailable() {
        return Zstd.isAvailable();
    }

    default ChannelHandler newBrotliEncoder() {
        return new BrotliEncoder();
    }

    default ChannelHandler newBrotliDecoder(int maxAllocation) {
        return BrotliDecoder.newDecoderWithMaxAllocation(maxAllocation);
    }

    default ChannelHandler newZstdEncoder(int compressionLevel, int maxEncodeSize) {
        return new ZstdEncoder(compressionLevel, StandardCompressionOptions.zstd().blockSize(), maxEncodeSize);
    }

    default ChannelHandler newZstdDecoder(int maxAllocation) {
        return new ZstdDecoder(maxAllocation);
    }

    default ChannelHandler newSnappyFrameEncoder() {
        return new SnappyFrameEncoder();
    }

    default ChannelHandler newSnappyFrameDecoder() {
        return new SnappyFrameDecoder();
    }
}
