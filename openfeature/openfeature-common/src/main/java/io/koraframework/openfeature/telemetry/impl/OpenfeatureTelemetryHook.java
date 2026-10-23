package io.koraframework.openfeature.telemetry.impl;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.Hook;
import dev.openfeature.sdk.HookContext;
import io.koraframework.openfeature.telemetry.OpenfeatureObservation;
import io.koraframework.openfeature.telemetry.OpenfeatureTelemetry;

import java.util.Map;
import java.util.Optional;

/** Bridges SDK evaluation callbacks to Kora observations, including fallback results. */
public final class OpenfeatureTelemetryHook implements Hook<Object> {

    private static final String OBSERVATION = "kora.openfeature.observation";
    private final OpenfeatureTelemetry telemetry;

    public OpenfeatureTelemetryHook(OpenfeatureTelemetry telemetry) {
        this.telemetry = telemetry;
    }

    @Override
    public Optional<EvaluationContext> before(HookContext<Object> context, Map<String, Object> hints) {
        context.getHookData().set(OBSERVATION, this.telemetry.observe(context));
        return Optional.empty();
    }

    @Override
    public void error(HookContext<Object> context, Exception error, Map<String, Object> hints) {
        var observation = context.getHookData().get(OBSERVATION, OpenfeatureObservation.class);
        if (observation != null) {
            observation.observeError(error);
        }
    }

    @Override
    public void finallyAfter(HookContext<Object> context, FlagEvaluationDetails<Object> details, Map<String, Object> hints) {
        var observation = context.getHookData().get(OBSERVATION, OpenfeatureObservation.class);
        if (observation != null) {
            try {
                observation.observeResult(details);
            } finally {
                observation.end();
            }
        }
    }
}
