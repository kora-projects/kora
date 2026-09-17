package io.koraframework.logging.logback.text.writer;

import ch.qos.logback.classic.spi.ILoggingEvent;
import io.koraframework.logging.common.arg.StructuredArgument;
import io.koraframework.logging.common.arg.StructuredArgumentWriter;

/**
 * Writes the structured arguments of a record, each on a line of its own as {@code name=json}: markers first, then
 * arguments, then key value pairs whose value is structured, plain key value pairs being left out.
 */
public final class DefaultStructuredTextWriter implements LoggingEventTextWriter {

    @Override
    public void write(StringBuilder out, ILoggingEvent event) {
        var markers = event.getMarkerList();
        if (markers != null) {
            for (var marker : markers) {
                if (marker instanceof StructuredArgument argument) {
                    line(out, argument.fieldName(), argument);
                }
            }
        }

        var arguments = event.getArgumentArray();
        if (arguments != null) {
            for (var argument : arguments) {
                if (argument instanceof StructuredArgument structured) {
                    line(out, structured.fieldName(), structured);
                }
            }
        }

        var keyValuePairs = event.getKeyValuePairs();
        if (keyValuePairs != null) {
            for (var pair : keyValuePairs) {
                if (pair.value instanceof StructuredArgumentWriter writer) {
                    line(out, pair.key, writer);
                }
            }
        }
    }

    private static void line(StringBuilder out, String name, StructuredArgumentWriter value) {
        out.append('\n').append('\t').append(name).append('=').append(value.writeToString());
    }
}
