package io.koraframework.openfeature.telemetry;

import dev.openfeature.sdk.FlagEvaluationDetails;
import io.koraframework.common.telemetry.Observation;

public interface OpenfeatureObservation extends Observation {

    void observeResult(FlagEvaluationDetails<?> result);
}
