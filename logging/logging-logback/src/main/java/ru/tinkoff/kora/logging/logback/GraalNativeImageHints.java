package ru.tinkoff.kora.logging.logback;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import ch.qos.logback.core.status.NopStatusListener;
import io.goodforgod.graalvm.hint.annotation.ReflectionHint;
import io.goodforgod.graalvm.hint.annotation.ResourceHint;

@ReflectionHint(types = {LayoutWrappingEncoder.class, NopStatusListener.class, AsyncAppender.class, KoraAsyncAppender.class})
@ResourceHint(include = {"logback.xml", "logback-test.xml"})
final class GraalNativeImageHints {

    private GraalNativeImageHints() {}
}
