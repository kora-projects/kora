package io.koraframework.openfeature.context;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ImmutableContext;
import io.koraframework.common.telemetry.OpentelemetryContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/** Request context carried by Kora's ScopedValue based OpenTelemetry context. */
public final class OpenfeatureContext {
    private static final ContextKey<EvaluationContext> KEY = ContextKey.named("kora.openfeature.context");

    private OpenfeatureContext() {}

    @Nullable
    public static EvaluationContext current() {
        return Context.current().get(KEY);
    }

    public static Context withContext(EvaluationContext context) {
        var snapshot = new ImmutableContext(context.getTargetingKey(), context.asMap());
        return new OpentelemetryContext(Context.current().with(KEY, snapshot));
    }

    public static void run(EvaluationContext context, Runnable task) {
        withContext(context).wrap(task).run();
    }

    public static <T> T call(EvaluationContext context, Supplier<T> task) {
        return withContext(context).wrapSupplier(task).get();
    }
}
