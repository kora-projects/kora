package ru.tinkoff.kora.common;

import io.goodforgod.graalvm.hint.annotation.InitializationHint;

@InitializationHint(types = Context.class)
final class GraalNativeImageHints {

    private GraalNativeImageHints() {}
}
