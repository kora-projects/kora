package io.koraframework.nats.common.producer;

import io.koraframework.config.common.annotation.ConfigMapper;

import java.time.Duration;

@ConfigMapper
public interface NatsAtomicBatchConfig {

    /**
     * Existing JetStream stream with allow_atomic enabled.
     */
    String stream();

    /**
     * Total time budget for sending and acknowledging all messages in a commit.
     */
    default Duration commitTimeout() {
        return Duration.ofSeconds(30);
    }

    default int maxMessages() {
        return 1000;
    }

    /**
     * Bound on buffered payload and header bytes.
     */
    default long maxBytes() {
        return 64L * 1024 * 1024;
    }
}
