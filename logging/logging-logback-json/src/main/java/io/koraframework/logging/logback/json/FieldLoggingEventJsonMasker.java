package io.koraframework.logging.logback.json;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class FieldLoggingEventJsonMasker implements LoggingEventJsonMasker {

    private final Set<String> fields;

    public FieldLoggingEventJsonMasker(Set<String> fields) {
        this.fields = fields.stream()
            .map(field -> field.toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public boolean shouldMask(String path, String fieldName) {
        return this.fields.contains(fieldName.toLowerCase(Locale.ROOT));
    }
}
