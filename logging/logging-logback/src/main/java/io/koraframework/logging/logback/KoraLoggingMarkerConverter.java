package io.koraframework.logging.logback;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.koraframework.logging.common.arg.StructuredArgument;

public final class KoraLoggingMarkerConverter extends ClassicConverter {
    @Override
    public String convert(ILoggingEvent event) {
        for (var marker : event.getMarkerList()) {
            if (marker instanceof StructuredArgument sa) {
                return sa.fieldName() + "=" + sa.writeToString();
            }
        }
        return "";
    }
}
