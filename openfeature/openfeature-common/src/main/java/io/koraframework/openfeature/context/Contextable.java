package io.koraframework.openfeature.context;

import dev.openfeature.sdk.EvaluationContext;

/** Creates a flag source with a snapshot of invocation context; the original source is unchanged. */
public interface Contextable<T> {
    T withContext(EvaluationContext ctx);
}
