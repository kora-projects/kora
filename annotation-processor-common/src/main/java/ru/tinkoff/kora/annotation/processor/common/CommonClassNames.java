package ru.tinkoff.kora.annotation.processor.common;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;

import java.util.List;


public class CommonClassNames {
    public static final ClassName nullable = ClassName.get("org.jspecify.annotations", "Nullable");
    public static final AnnotationSpec nullableAnnotation = AnnotationSpec.builder(nullable).build();
    public static final ClassName nonnull = ClassName.get("org.jspecify.annotations", "NonNull");
    public static final AnnotationSpec nonNullAnnotation = AnnotationSpec.builder(nonnull).build();
    public static final ClassName observation = ClassName.get("ru.tinkoff.kora.common.telemetry", "Observation");
    public static final ClassName opentelemetryContext = ClassName.get("ru.tinkoff.kora.common.telemetry", "OpentelemetryContext");
    public static final ClassName contextOpentelemetry = ClassName.get("io.opentelemetry.context", "Context");
    public static final ClassName publisher = ClassName.get("org.reactivestreams", "Publisher");
    public static final ClassName mono = ClassName.get("reactor.core.publisher", "Mono");
    public static final ClassName flux = ClassName.get("reactor.core.publisher", "Flux");

    public static final ClassName root = ClassName.get("ru.tinkoff.kora.common.annotation", "Root");
    public static final ClassName aopAnnotation = ClassName.get("ru.tinkoff.kora.common", "AopAnnotation");
    public static final ClassName aopProxy = ClassName.get("ru.tinkoff.kora.common", "AopProxy");
    public static final ClassName mapping = ClassName.get("ru.tinkoff.kora.common", "Mapping");
    public static final ClassName mappings = ClassName.get("ru.tinkoff.kora.common", "Mapping", "Mappings");
    public static final ClassName namingStrategy = ClassName.get("ru.tinkoff.kora.common", "NamingStrategy");
    public static final ClassName tag = ClassName.get("ru.tinkoff.kora.common", "Tag");
    public static final ClassName tagAny = ClassName.get("ru.tinkoff.kora.common", "Tag", "Any");
    public static final ClassName nameConverter = ClassName.get("ru.tinkoff.kora.common.naming", "NameConverter");
    public static final ClassName koraApp = ClassName.get("ru.tinkoff.kora.common", "KoraApp");
    public static final ClassName koraSubmodule = ClassName.get("ru.tinkoff.kora.common", "KoraSubmodule");
    public static final ClassName module = ClassName.get("ru.tinkoff.kora.common", "Module");
    public static final ClassName component = ClassName.get("ru.tinkoff.kora.common", "Component");
    public static final ClassName defaultComponent = ClassName.get("ru.tinkoff.kora.common", "DefaultComponent");

    public static final ClassName node = ClassName.get("ru.tinkoff.kora.application.graph", "Node");
    public static final ClassName lifecycle = ClassName.get("ru.tinkoff.kora.application.graph", "Lifecycle");
    public static final ClassName all = ClassName.get("ru.tinkoff.kora.application.graph", "All");
    public static final ClassName typeRef = ClassName.get("ru.tinkoff.kora.application.graph", "TypeRef");
    public static final ClassName wrapped = ClassName.get("ru.tinkoff.kora.application.graph", "Wrapped");
    public static final ClassName wrappedUnwrappedValue = ClassName.get("ru.tinkoff.kora.application.graph", "Wrapped", "UnwrappedValue");
    public static final ClassName promiseOf = ClassName.get("ru.tinkoff.kora.application.graph", "PromiseOf");
    public static final ClassName valueOf = ClassName.get("ru.tinkoff.kora.application.graph", "ValueOf");
    public static final ClassName applicationGraphDraw = ClassName.get("ru.tinkoff.kora.application.graph", "ApplicationGraphDraw");
    public static final ClassName graphInterceptor = ClassName.get("ru.tinkoff.kora.application.graph", "GraphInterceptor");
    public static final ClassName promisedProxy = ClassName.get("ru.tinkoff.kora.common", "PromisedProxy");
    public static final ClassName refreshListener = ClassName.get("ru.tinkoff.kora.application.graph", "RefreshListener");

    public static final ClassName koraGenerated = ClassName.get("ru.tinkoff.kora.common.annotation", "Generated");
    public static final ClassName list = ClassName.get(List.class);

    public static final ClassName config = ClassName.get("ru.tinkoff.kora.config.common", "Config");
    public static final ClassName configValueExtractor = ClassName.get("ru.tinkoff.kora.config.common.extractor", "ConfigValueExtractor");
    public static final ClassName configValueExtractorAnnotation = ClassName.get("ru.tinkoff.kora.config.common.annotation", "ConfigValueExtractor");
    public static final ClassName configValue = ClassName.get("ru.tinkoff.kora.config.common", "ConfigValue");
    public static final ClassName configValueExtractionException = ClassName.get("ru.tinkoff.kora.config.common.extractor", "ConfigValueExtractionException");

    public static final ClassName telemetryConfig = ClassName.get("ru.tinkoff.kora.telemetry.common", "TelemetryConfig");
    public static final ClassName meterRegistry = ClassName.get("io.micrometer.core.instrument", "MeterRegistry");
}
