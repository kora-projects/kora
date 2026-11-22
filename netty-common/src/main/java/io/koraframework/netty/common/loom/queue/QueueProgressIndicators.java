package io.koraframework.netty.common.loom.queue;

public interface QueueProgressIndicators {

    long currentProducerIndex();

    long currentConsumerIndex();
}
