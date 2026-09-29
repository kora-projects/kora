package io.koraframework.logging.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.LoggingEvent;
import io.koraframework.logging.common.arg.StructuredArgument;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KoraLoggingMarkerConverterTest {

    @Test
    void rendersNothingForEventWithoutMarkers() {
        var layout = layout();

        assertThat(layout.doLayout(event("started"))).isEqualTo(" started");
    }

    @Test
    void rendersStructuredArgumentMarker() {
        var layout = layout();
        var event = event("handled");
        event.addMarker(StructuredArgument.marker("requestId", "42"));

        assertThat(layout.doLayout(event)).isEqualTo("requestId=\"42\" handled");
    }

    private static PatternLayout layout() {
        var layout = new PatternLayout();
        layout.setContext(new LoggerContext());
        layout.getInstanceConverterMap().put("koraMarker", KoraLoggingMarkerConverter::new);
        layout.setPattern("%koraMarker %msg");
        layout.start();
        return layout;
    }

    private static LoggingEvent event(String message) {
        var context = new LoggerContext();
        return new LoggingEvent(KoraLoggingMarkerConverterTest.class.getName(), context.getLogger("test"), Level.INFO, message, null, null);
    }
}
