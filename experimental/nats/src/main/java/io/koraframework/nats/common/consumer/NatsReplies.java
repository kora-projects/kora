package io.koraframework.nats.common.consumer;

import io.koraframework.common.telemetry.Observation;
import io.koraframework.nats.common.NatsClient;
import io.koraframework.nats.common.producer.serializer.NatsSerializer;
import io.nats.client.impl.Headers;

/**
 * Generated listener response serialization and publishing share one observed operation.
 */
public final class NatsReplies {
    private NatsReplies() {
    }

    public static <T> void reply(NatsMessage<?> request, NatsClient client, T response, NatsSerializer<T> serializer) {
        var subject = request.replyTo();
        if (subject == null) {
            return;
        }
        var observation = request.observation().observeReply(subject);
        try {
            Observation.scoped(observation).run(() -> {
                var headers = new Headers();
                observation.observeData(response);
                var message = io.nats.client.impl.NatsMessage.builder().subject(subject).headers(headers).data(serializer.serialize(subject, headers, response)).build();
                observation.observeRecord(message);
                client.connection().publish(message);
            });
        } catch (RuntimeException | Error e) {
            observation.observeError(e);
            throw e;
        } finally {
            observation.end();
        }
    }
}
