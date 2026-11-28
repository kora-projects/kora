/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.koraframework.http.server.netty.compression;

import io.netty.handler.codec.compression.*;

/**
 * Stock Netty codec construction.
 */
public final class DefaultNettyZlibCodecFactory implements NettyZlibCodecFactory {
    public static final NettyZlibCodecFactory INSTANCE = new DefaultNettyZlibCodecFactory();

    @Override
    public ZlibEncoder newZlibEncoder(ZlibWrapper wrapper, int compressionLevel, int windowBits, int memLevel) {
        return ZlibCodecFactory.newZlibEncoder(wrapper, compressionLevel, windowBits, memLevel);
    }

    @Override
    public ZlibDecoder newZlibDecoder(ZlibWrapper wrapper, int maxAllocation) {
        return ZlibCodecFactory.newZlibDecoder(wrapper, maxAllocation);
    }
}
