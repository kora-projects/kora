package io.koraframework.kafka.common.producer.telemetry.impl;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import io.koraframework.logging.common.MDC;

class DefaultKafkaPublisherRecordObservationTest {

    @Test
    void observationIsCreatedOutsideAnyMdcScope() {
        // publishing from a scheduled job, at startup or in a test happens with MDC.VALUE unbound
        assertThatCode(() -> new DefaultKafkaPublisherRecordObservation(null, null, null, "topic", null))
                .doesNotThrowAnyException();
    }

    @Test
    void observationForksTheBoundMdc() {
        var mdc = new MDC();
        mdc.put0("key", "value");

        ScopedValue.where(MDC.VALUE, mdc).run(() ->
                assertThatCode(() -> new DefaultKafkaPublisherRecordObservation(null, null, null, "topic", null))
                        .doesNotThrowAnyException());
    }
}
