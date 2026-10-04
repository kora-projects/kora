package io.koraframework.openfeature;

import dev.openfeature.sdk.EventDetails;

/** Receives provider events on the SDK event executor. Implementations must be thread safe. */
public interface OpenfeatureEventListener {
    default void onReady(EventDetails details) {}
    default void onError(EventDetails details) {}
    default void onStale(EventDetails details) {}
    default void onConfigurationChanged(EventDetails details) {}
}
