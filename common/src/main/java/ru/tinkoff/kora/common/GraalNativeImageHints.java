package ru.tinkoff.kora.common;

import io.goodforgod.graalvm.hint.annotation.InitializationHint;
import io.goodforgod.graalvm.hint.annotation.NativeImageHint;
import ru.tinkoff.kora.common.util.ReactorContextHook;

@NativeImageHint(optionNames = {"--install-exit-handlers"})
@InitializationHint(types = {
    ReactorContextHook.class,
    Context.class
})
final class GraalNativeImageHints {

    private GraalNativeImageHints() {}
}
