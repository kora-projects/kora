package io.koraframework.http.common.telemetry;

import io.koraframework.http.common.header.HttpHeaders;
import io.koraframework.logging.common.masking.MaskingStrategy;

import java.util.*;

public final class MaskingUtils {

    public static final int AVERAGE_HEADER_SIZE = 15;

    private MaskingUtils() { }

    public static String toMaskedString(Set<String> maskedHeaders, String mask, HttpHeaders headers) {
        return toMaskedString(maskedHeaders, new FixedMaskStrategy(mask), headers);
    }

    public static String toMaskedString(Set<String> maskedHeaders, MaskingStrategy maskingStrategy, HttpHeaders headers) {
        var sb = new StringBuilder(headers.size() * AVERAGE_HEADER_SIZE);
        var iterator = headers.iterator();
        while (iterator.hasNext()) {
            var headerEntry = iterator.next();
            // В HttpHeaders все заголовки в нижнем регистре, приведение не требуется
            var headerKey = headerEntry.getKey();
            var headerValues = headerEntry.getValue();
            sb.append(headerKey).append(": ");

            if (maskedHeaders.contains(headerKey)) {
                for (int i = 0; i < headerValues.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(maskingStrategy.mask(headerValues.get(i)));
                }
            } else {
                sb.append(String.join(", ", headerValues));
            }

            if (iterator.hasNext()) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    public static String toMaskedString(Set<String> maskedQueryParams, MaskingStrategy maskingStrategy, Map<String, ? extends Collection<String>> queryParams) {
        var sb = new StringBuilder(queryParams.size() * AVERAGE_HEADER_SIZE);
        for (var e : queryParams.entrySet()) {
            var key = e.getKey();
            var values = e.getValue();
            if (values.isEmpty()) {
                if (!sb.isEmpty()) {
                    sb.append('&');
                }
                sb.append(key).append('=');
            } else {
                for (var value : values) {
                    if (!sb.isEmpty()) {
                        sb.append('&');
                    }
                    sb.append(key).append('=').append(maskedQueryParams.contains(key.toLowerCase(Locale.ROOT))
                        ? maskingStrategy.mask(value)
                        : value);
                }
            }
        }
        return sb.toString();
    }

    public static String toMaskedString(Set<String> maskedQueryParams, String mask, Map<String, ? extends Collection<String>> queryParams) {
        return toMaskedString(maskedQueryParams, new FixedMaskStrategy(mask), queryParams);
    }

    public static String toMaskedString(Set<String> maskedQueryParams, MaskingStrategy maskingStrategy, String queryParams) {
        if (maskedQueryParams.isEmpty()) {
            return queryParams;
        }

        var sb = new StringBuilder(queryParams.length());
        int start = 0;
        int len = queryParams.length();

        while (start < len) {
            int nextAmp = queryParams.indexOf('&', start);
            int end = (nextAmp == -1) ? len : nextAmp;

            int eqIdx = queryParams.indexOf('=', start);
            if (eqIdx == -1 || eqIdx > end) {
                sb.append(queryParams, start, end);
            } else {
                String paramName = queryParams.substring(start, eqIdx);
                if (maskedQueryParams.contains(paramName.toLowerCase(Locale.ROOT))) {
                    String value = queryParams.substring(eqIdx + 1, end);
                    sb.append(paramName).append('=').append(maskingStrategy.mask(value));
                } else {
                    sb.append(queryParams, start, end);
                }
            }

            if (nextAmp != -1) {
                sb.append('&');
            }
            start = end + 1;
        }

        return sb.toString();
    }

    public static String toMaskedString(Set<String> maskedQueryParams, String mask, String queryParams) {
        return toMaskedString(maskedQueryParams, new FixedMaskStrategy(mask), queryParams);
    }

    private static final class FixedMaskStrategy implements MaskingStrategy {
        private final String mask;

        public FixedMaskStrategy(String mask) {
            this.mask = mask;
        }

        @Override
        public String mask(Object value) {
            return this.mask;
        }
    }
}
