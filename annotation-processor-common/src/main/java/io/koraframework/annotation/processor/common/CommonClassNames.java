package io.koraframework.annotation.processor.common;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;

import java.util.List;


public class CommonClassNames {
    public static final ClassName nullable = ClassName.get("org.jspecify.annotations", "Nullable");
    public static final AnnotationSpec nullableAnnotation = AnnotationSpec.builder(nullable).build();
    public static final ClassName nonnull = ClassName.get("org.jspecify.annotations", "NonNull");
    public static final AnnotationSpec nonNullAnnotation = AnnotationSpec.builder(nonnull).build();
    public static final ClassName observation = ClassName.get("io.koraframework.common.telemetry", "Observation");
    public static final ClassName opentelemetryContext = ClassName.get("io.koraframework.common.telemetry", "OpentelemetryContext");
    public static final ClassName contextOpentelemetry = ClassName.get("io.opentelemetry.context", "Context");
    public static final ClassName publisher = ClassName.get("org.reactivestreams", "Publisher");
    public static final ClassName mono = ClassName.get("reactor.core.publisher", "Mono");
    public static final ClassName flux = ClassName.get("reactor.core.publisher", "Flux");

    public static final ClassName root = ClassName.get("io.koraframework.common.annotation", "Root");
    public static final ClassName aopAnnotation = ClassName.get("io.koraframework.common", "AopAnnotation");
    public static final ClassName aopProxy = ClassName.get("io.koraframework.common", "AopProxy");
    public static final ClassName mapping = ClassName.get("io.koraframework.common", "Mapping");
    public static final ClassName mappings = ClassName.get("io.koraframework.common", "Mapping", "Mappings");
    public static final ClassName namingStrategy = ClassName.get("io.koraframework.common", "NamingStrategy");
    public static final ClassName tag = ClassName.get("io.koraframework.common", "Tag");
    public static final ClassName tagAny = ClassName.get("io.koraframework.common", "Tag", "Any");
    public static final ClassName nameConverter = ClassName.get("io.koraframework.common.naming", "NameConverter");
    public static final ClassName koraApp = ClassName.get("io.koraframework.common", "KoraApp");
    public static final ClassName koraSubmodule = ClassName.get("io.koraframework.common", "KoraSubmodule");
    public static final ClassName module = ClassName.get("io.koraframework.common", "Module");
    public static final ClassName component = ClassName.get("io.koraframework.common", "Component");
    public static final ClassName defaultComponent = ClassName.get("io.koraframework.common", "DefaultComponent");

    public static final ClassName node = ClassName.get("io.koraframework.application.graph", "Node");
    public static final ClassName lifecycle = ClassName.get("io.koraframework.application.graph", "Lifecycle");
    public static final ClassName all = ClassName.get("io.koraframework.application.graph", "All");
    public static final ClassName typeRef = ClassName.get("io.koraframework.application.graph", "TypeRef");
    public static final ClassName wrapped = ClassName.get("io.koraframework.application.graph", "Wrapped");
    public static final ClassName wrappedUnwrappedValue = ClassName.get("io.koraframework.application.graph", "Wrapped", "UnwrappedValue");
    public static final ClassName promiseOf = ClassName.get("io.koraframework.application.graph", "PromiseOf");
    public static final ClassName valueOf = ClassName.get("io.koraframework.application.graph", "ValueOf");
    public static final ClassName applicationGraphDraw = ClassName.get("io.koraframework.application.graph", "ApplicationGraphDraw");
    public static final ClassName graphInterceptor = ClassName.get("io.koraframework.application.graph", "GraphInterceptor");
    public static final ClassName promisedProxy = ClassName.get("io.koraframework.common", "PromisedProxy");
    public static final ClassName refreshListener = ClassName.get("io.koraframework.application.graph", "RefreshListener");

    public static final ClassName koraGenerated = ClassName.get("io.koraframework.common.annotation", "Generated");
    public static final ClassName list = ClassName.get(List.class);

    public static final ClassName config = ClassName.get("io.koraframework.config.common", "Config");
    public static final ClassName configValueExtractor = ClassName.get("io.koraframework.config.common.extractor", "ConfigValueExtractor");
    public static final ClassName configValueExtractorAnnotation = ClassName.get("io.koraframework.config.common.annotation", "ConfigValueExtractor");
    public static final ClassName configValue = ClassName.get("io.koraframework.config.common", "ConfigValue");
    public static final ClassName configValueExtractionException = ClassName.get("io.koraframework.config.common.extractor", "ConfigValueExtractionException");

    public static final ClassName telemetryConfig = ClassName.get("io.koraframework.telemetry.common", "TelemetryConfig");
    public static final ClassName meterRegistry = ClassName.get("io.micrometer.core.instrument", "MeterRegistry");
}
