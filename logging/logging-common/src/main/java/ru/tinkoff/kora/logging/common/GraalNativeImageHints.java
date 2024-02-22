package ru.tinkoff.kora.logging.common;

import io.goodforgod.graalvm.hint.annotation.InitializationHint;

@InitializationHint(types = { MDC.class })
final class GraalNativeImageHints {

    private GraalNativeImageHints() { }
}
