package io.koraframework.openfeature.context;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.TransactionContextPropagator;
import org.jspecify.annotations.Nullable;

/** Reads transaction context from Kora's scoped request context. */
public final class OpenfeatureTransactionContextPropagator implements TransactionContextPropagator {
    @Override
    @Nullable
    public EvaluationContext getTransactionContext() {
        return OpenfeatureContext.current();
    }

    @Override
    public void setTransactionContext(EvaluationContext context) {
        throw new UnsupportedOperationException("Use OpenfeatureContext.run/call to bind scoped transaction context");
    }
}
