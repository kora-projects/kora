package io.koraframework.nats.common.exceptions;

/**
 * Commit failure with an explicit distinction between rejection and an unknown broker outcome.
 */
public final class NatsAtomicBatchException extends NatsPublishException {
    public enum Outcome {
        ABORTED, UNKNOWN
    }

    private final String batchId;
    private final Outcome outcome;

    public NatsAtomicBatchException(String batchId, Outcome outcome, Throwable cause) {
        super("NATS atomic batch " + batchId + " commit failed; outcome=" + outcome, cause);
        this.batchId = batchId;
        this.outcome = outcome;
    }

    public String batchId() {
        return batchId;
    }

    public Outcome outcome() {
        return outcome;
    }
}
