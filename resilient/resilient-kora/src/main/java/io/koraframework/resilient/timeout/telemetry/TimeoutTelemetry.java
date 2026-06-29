package io.koraframework.resilient.timeout.telemetry;

import java.time.Duration;

public interface TimeoutTelemetry {

    TimeoutObservation observe(Duration timeToWait);
}
