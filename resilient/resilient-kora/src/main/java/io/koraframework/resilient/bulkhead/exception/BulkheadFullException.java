package io.koraframework.resilient.bulkhead.exception;

import io.koraframework.resilient.circuitbreaker.NonCircuitableException;
import io.koraframework.resilient.exception.ResilientException;
import io.koraframework.resilient.retry.NonRetryableException;

/**
 * Local saturation is not evidence of a downstream failure.
 */
public final class BulkheadFullException extends ResilientException implements NonCircuitableException, NonRetryableException {

    public enum Reason {
        SATURATED, QUEUE_FULL, QUEUE_TIMEOUT, INTERRUPTED, SHUTDOWN
    }

    private final Reason reason;

    public BulkheadFullException(String name) {
        this(name, Reason.SATURATED);
    }

    public BulkheadFullException(String name, Reason reason) {
        super(name, "Bulkhead '" + name + "' admission rejected: " + reason);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
