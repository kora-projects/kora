package io.koraframework.openfeature.telemetry;

import dev.openfeature.sdk.HookContext;

public interface OpenfeatureTelemetry {

    OpenfeatureObservation observe(HookContext<?> evaluation);
}
