package ru.tinkoff.kora.http.server.undertow;

import io.goodforgod.graalvm.hint.annotation.InitializationHint;
import io.goodforgod.graalvm.hint.annotation.ReflectionHint;
import io.undertow.server.DirectByteBufferDeallocator;

@InitializationHint(types = {DirectByteBufferDeallocator.class})
@ReflectionHint(types = {DirectByteBufferDeallocator.class})
final class GraalNativeImageHints {

    private GraalNativeImageHints() {}
}
