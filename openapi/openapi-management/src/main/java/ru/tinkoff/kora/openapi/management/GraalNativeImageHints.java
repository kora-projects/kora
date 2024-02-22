package ru.tinkoff.kora.openapi.management;

import io.goodforgod.graalvm.hint.annotation.ResourceHint;

@ResourceHint(include = {
    "kora/openapi/management/rapidoc/index.html",
    "kora/openapi/management/swagger-ui/index.html"
})
final class GraalNativeImageHints {

    private GraalNativeImageHints() { }
}
