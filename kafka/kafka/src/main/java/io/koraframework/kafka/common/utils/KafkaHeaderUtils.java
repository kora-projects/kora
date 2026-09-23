package io.koraframework.kafka.common.utils;

import io.koraframework.logging.common.masking.MaskingStrategy;
import org.apache.kafka.common.header.Headers;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

public final class KafkaHeaderUtils {

    private KafkaHeaderUtils() {}

    public static String toMaskedString(Set<String> maskedHeaders, MaskingStrategy maskingStrategy, Headers headers) {
        var result = new StringBuilder();
        for (var header : headers) {
            if (!result.isEmpty()) {
                result.append('\n');
            }
            var value = header.value();
            result.append(header.key()).append(": ");
            if (maskedHeaders.contains(header.key().toLowerCase(Locale.ROOT))) {
                result.append(maskingStrategy.mask(value));
            } else if (value != null) {
                result.append(new String(value, StandardCharsets.UTF_8));
            }
        }
        return result.toString();
    }
}
