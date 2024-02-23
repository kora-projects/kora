package ru.tinkoff.kora.application.graph;

import io.goodforgod.graalvm.hint.annotation.InitializationHint;
import io.goodforgod.graalvm.hint.annotation.NativeImageHint;
import ru.tinkoff.kora.application.graph.internal.loom.VirtualThreadExecutorHolder;

@NativeImageHint(optionNames = {"--install-exit-handlers"})
@InitializationHint(types = VirtualThreadExecutorHolder.class)
final class GraalNativeImageHints {

    private GraalNativeImageHints() {}
}
