package ru.tinkoff.kora.logging.logback;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.model.processor.LogbackClassicDefaultNestedComponentRules;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import ch.qos.logback.core.model.processor.ChainedModelFilter;
import ch.qos.logback.core.model.processor.DefaultProcessor;
import ch.qos.logback.core.model.processor.ImplicitModelHandler;
import ch.qos.logback.core.status.InfoStatus;
import ch.qos.logback.core.status.NopStatusListener;
import ch.qos.logback.core.status.StatusBase;
import ch.qos.logback.core.subst.NodeToStringTransformer;
import ch.qos.logback.core.subst.Parser;
import ch.qos.logback.core.subst.Token;
import ch.qos.logback.core.util.Duration;
import ch.qos.logback.core.util.Loader;
import io.goodforgod.graalvm.hint.annotation.InitializationHint;
import io.goodforgod.graalvm.hint.annotation.ReflectionHint;
import io.goodforgod.graalvm.hint.annotation.ResourceHint;
import org.slf4j.LoggerFactory;

@ReflectionHint(types = {LayoutWrappingEncoder.class, NopStatusListener.class, AsyncAppender.class, KoraAsyncAppender.class})
@ResourceHint(include = {"logback.xml", "logback-test.xml"})
final class GraalNativeImageHints {

    private GraalNativeImageHints() {}
}
