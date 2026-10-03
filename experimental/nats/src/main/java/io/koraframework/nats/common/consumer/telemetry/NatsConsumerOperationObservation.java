package io.koraframework.nats.common.consumer.telemetry;

import io.koraframework.common.telemetry.Observation;
import org.jspecify.annotations.Nullable;

/**
 * A declarative listener operation, including acknowledgements and replies.
 */
public interface NatsConsumerOperationObservation extends Observation {
    default void observeResult(@Nullable Object result) {
    }

    default void end(@Nullable Throwable error) {
        if (error != null) {
            observeError(error);
        }
        end();
    }
}
