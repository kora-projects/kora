package io.koraframework.http.common.telemetry;

import io.koraframework.http.common.header.HttpHeaders;

import java.util.*;
import java.util.stream.Collectors;

public final class MaskingUtils {

    public static final int AVERAGE_HEADER_SIZE = 15;

    private MaskingUtils() { }

    public static String toMaskedString(Set<String> maskedHeaders, String mask, HttpHeaders headers) {
        return toMaskedString(maskedHeaders, (key, value) -> mask, headers);
    }

    public static String toMaskedString(Set<String> maskedHeaders, HttpArgMaskingStrategy maskingStrategy, HttpHeaders headers) {
        var sb = new StringBuilder(headers.size() * AVERAGE_HEADER_SIZE);
        var iterator = headers.iterator();
        while (iterator.hasNext()) {
            var headerEntry = iterator.next();
            // В HttpHeaders все заголовки в нижнем регистре, приведение не требуется
            var headerKey = headerEntry.getKey();
            var headerValues = headerEntry.getValue();
            sb.append(headerKey)
                .append(": ")
                .append(maskedHeaders.contains(headerKey)
                    ? headerValues.stream().map(value -> maskingStrategy.mask(headerKey, value)).collect(Collectors.joining(", "))
                    : String.join(", ", headerValues));
            if (iterator.hasNext()) {
                sb.append('\n');
            }
        }
        return sb.toString();

    }

    public static String toMaskedString(Set<String> maskedQueryParams, HttpArgMaskingStrategy maskingStrategy, Map<String, ? extends Collection<String>> queryParams) {
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
                        ? maskingStrategy.mask(key, value)
                        : value);
                }
            }
        }
        return sb.toString();
    }

    public static String toMaskedString(Set<String> maskedQueryParams, String mask, Map<String, ? extends Collection<String>> queryParams) {
        return toMaskedString(maskedQueryParams, (key, value) -> mask, queryParams);
    }

    public static String toMaskedString(Set<String> maskedQueryParams, HttpArgMaskingStrategy maskingStrategy, String queryParams) {
        if (maskedQueryParams.isEmpty()) {
            return queryParams;
        }

        return Arrays.stream(queryParams.split("&"))
            .map(str -> {
                final int i = str.indexOf('=');
                if (i == -1) {
                    return str;
                }
                final String paramName = str.substring(0, i);
                if (maskedQueryParams.contains(paramName.toLowerCase(Locale.ROOT))) {
                    var value = str.substring(i + 1);
                    return paramName + '=' + maskingStrategy.mask(paramName, value);
                } else {
                    return str;
                }
            })
            .collect(Collectors.joining("&"));
    }

    public static String toMaskedString(Set<String> maskedQueryParams, String mask, String queryParams) {
        return toMaskedString(maskedQueryParams, (key, value) -> mask, queryParams);
    }

}
