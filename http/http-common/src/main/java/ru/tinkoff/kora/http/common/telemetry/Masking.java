package ru.tinkoff.kora.http.common.telemetry;

import ru.tinkoff.kora.http.common.header.HttpHeaders;

import java.util.*;
import java.util.stream.Collectors;

public class Masking {
    public static final int AVERAGE_HEADER_SIZE = 15;

    public static String toMaskedString(Set<String> maskedHeaders, String mask, HttpHeaders headers) {
        var sb = new StringBuilder(headers.size() * AVERAGE_HEADER_SIZE);
        var iterator = headers.iterator();
        while (iterator.hasNext()) {
            var headerEntry = iterator.next();
            // В HttpHeaders все заголовки в нижнем регистре, приведение не требуется
            var headerKey = headerEntry.getKey();
            var headerValues = headerEntry.getValue();
            sb.append(headerKey)
                .append(": ")
                .append(maskedHeaders.contains(headerKey) ? mask : String.join(", ", headerValues));
            if (iterator.hasNext()) {
                sb.append('\n');
            }
        }
        return sb.toString();

    }

    public static String toMaskedString(Set<String> maskedQueryParams, String mask, Map<String, ? extends Collection<String>> queryParams) {
        var sb = new StringBuilder(queryParams.size() * AVERAGE_HEADER_SIZE);
        for (var e : queryParams.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            var key = e.getKey();
            var values = e.getValue();
            if (maskedQueryParams.contains(key.toLowerCase(Locale.ROOT))) {
                sb.append(key).append('=').append(mask);
            } else if (values.isEmpty()) {
                sb.append(key).append('=');
            } else {
                for (var value : values) {
                    if (!sb.isEmpty()) {
                        sb.append('&');
                    }
                    sb.append(key).append('=').append(value);
                }
            }
        }
        return sb.toString();
    }

    public static String toMaskedString(Set<String> maskedQueryParams, String mask, String queryParams) {
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
                    return paramName + '=' + mask;
                } else {
                    return str;
                }
            })
            .collect(Collectors.joining("&"));
    }

}
