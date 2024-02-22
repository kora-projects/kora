package ru.tinkoff.kora.application.graph;

import io.goodforgod.graalvm.hint.annotation.InitializationHint;
import ru.tinkoff.kora.application.graph.internal.loom.VirtualThreadExecutorHolder;

@InitializationHint(types = VirtualThreadExecutorHolder.class)
final class GraalNativeImageHints {

    private GraalNativeImageHints() {}
}
