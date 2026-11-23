package io.koraframework.nats.common.consumer;

import java.util.Iterator;
import java.util.List;

public record NatsMessages<T>(List<NatsMessage<T>> messages) implements Iterable<NatsMessage<T>> {
    public NatsMessages {
        messages = List.copyOf(messages);
    }

    public int count() {
        return messages.size();
    }

    public boolean isEmpty() {
        return messages.isEmpty();
    }

    @Override
    public Iterator<NatsMessage<T>> iterator() {
        return messages.iterator();
    }
}
