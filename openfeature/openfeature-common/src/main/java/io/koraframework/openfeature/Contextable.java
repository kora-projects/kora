package io.koraframework.openfeature;

import dev.openfeature.sdk.EvaluationContext;

public interface Contextable<T> {
    T withContext(EvaluationContext ctx);
}
