package io.koraframework.kafka.common.containers.handlers;

import io.koraframework.kafka.common.consumer.containers.handlers.BaseKafkaRecordsHandler;
import io.koraframework.kafka.common.consumer.containers.handlers.impl.RecordHandler;
import io.koraframework.kafka.common.consumer.containers.handlers.impl.RecordsHandler;
import io.koraframework.kafka.common.consumer.containers.handlers.wrapper.HandlerWrapper;
import io.koraframework.kafka.common.consumer.telemetry.KafkaConsumerPollObservation;
import io.koraframework.kafka.common.consumer.telemetry.KafkaConsumerRecordObservation;
import io.koraframework.kafka.common.consumer.telemetry.impl.NoopKafkaConsumerPollObservation;
import io.koraframework.kafka.common.consumer.telemetry.impl.NoopKafkaConsumerRecordObservation;
import org.apache.kafka.clients.consumer.CommitFailedException;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HandlersObservationTest {

    @Test
    void recordsHandlerEndsObservationOnEmptyPoll() {
        var observation = pollObservation();
        var handler = new RecordsHandler<String, String>(true, () -> (consumer, o, records) -> {}, false);

        handler.handle(observation, ConsumerRecords.empty(), null, true);

        Mockito.verify(observation).end();
    }

    @Test
    void wrappedHandlerEndsObservationOnEmptyPoll() {
        var observation = pollObservation();
        var real = Mockito.mock(BaseKafkaRecordsHandler.class);
        @SuppressWarnings("unchecked")
        var handler = HandlerWrapper.<String, String>wrapHandler(() -> real, false);

        handler.handle(observation, ConsumerRecords.empty(), null, true);

        Mockito.verify(observation).end();
        Mockito.verifyNoInteractions(real);
    }

    @Test
    @SuppressWarnings("unchecked")
    void failedCommitRetryOnWakeupIsObserved() {
        var recordObservation = Mockito.mock(KafkaConsumerRecordObservation.class, AdditionalAnswers.delegatesTo(NoopKafkaConsumerRecordObservation.INSTANCE));
        var observation = pollObservation();
        Mockito.doReturn(recordObservation).when(observation).observeRecord(Mockito.any());
        var consumer = (Consumer<String, String>) Mockito.mock(Consumer.class);
        var commitFailed = new CommitFailedException();
        Mockito.doThrow(new WakeupException()).doThrow(commitFailed).when(consumer).commitSync(Mockito.<Map>any());
        var partition = new TopicPartition("topic", 0);
        var records = new ConsumerRecords<>(Map.of(partition, List.of(new ConsumerRecord<>("topic", 0, 0, "k", "v"))), Map.of());
        var handler = new RecordHandler<String, String>(true, () -> (c, o, record) -> {});

        assertThatThrownBy(() -> handler.handle(observation, records, consumer, true)).isSameAs(commitFailed);

        Mockito.verify(recordObservation).observeError(commitFailed);
        Mockito.verify(recordObservation).end();
        Mockito.verify(observation).observeError(commitFailed);
        Mockito.verify(observation).end();
    }

    private static KafkaConsumerPollObservation pollObservation() {
        return Mockito.mock(KafkaConsumerPollObservation.class, AdditionalAnswers.delegatesTo(NoopKafkaConsumerPollObservation.INSTANCE));
    }
}
