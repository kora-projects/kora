package ru.tinkoff.kora.config.hocon;

import io.goodforgod.graalvm.hint.annotation.ResourceHint;

@ResourceHint(include = {"application.conf", "reference.conf"})
final class GraalNativeImageHints {

    private GraalNativeImageHints() { }
}
