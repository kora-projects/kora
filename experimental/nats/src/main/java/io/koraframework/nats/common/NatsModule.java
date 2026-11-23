package io.koraframework.nats.common;

import io.koraframework.nats.common.consumer.NatsListenerModule;
import io.koraframework.nats.common.producer.NatsPublisherModule;

/**
 * Defaults only; annotation processors register individually tagged connections.
 */
public interface NatsModule extends NatsListenerModule, NatsPublisherModule {
}
