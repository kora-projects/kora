package io.koraframework.logging.logback;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.koraframework.logging.common.arg.StructuredArgument;

public final class KoraLoggingMarkerConverter extends ClassicConverter {
    @Override
    public String convert(ILoggingEvent event) {
        var markers = event.getMarkerList();
        if (markers != null) {
            for (var marker : markers) {
                if (marker instanceof StructuredArgument sa) {
                    return sa.fieldName() + "=" + sa.writeToString();
                }
            }
        }
        return "";
    }
}
