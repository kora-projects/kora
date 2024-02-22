package ru.tinkoff.kora.config.yaml;

import io.goodforgod.graalvm.hint.annotation.ResourceHint;

@ResourceHint(include = {"application.yaml", "application.yml", "reference.yaml", "reference.yml"})
final class GraalNativeImageHints {

    private GraalNativeImageHints() { }
}
