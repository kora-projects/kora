package ru.tinkoff.kora.http.common.masking;

import ru.tinkoff.kora.http.common.header.HttpHeaders;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class MaskUtils {

    private static final int AVERAGE_HEADER_SIZE = 15;
    private static final String MASK_FILLER = "***";

    private MaskUtils() {
    }

    public static String toMaskedString(HttpHeaders headers, Collection<String> maskedHeaders) {
        final Set<String> maskedHeadersSet = toLowerCaseSet(maskedHeaders);
        var sb = new StringBuilder(headers.size() * AVERAGE_HEADER_SIZE);
        headers.forEach((headerEntry) -> {
            // В HttpHeaders все заголовки в нижнем регистре, приведение не требуется
            final String headerKey = headerEntry.getKey();
            final List<String> headerValues = headerEntry.getValue();
            sb.append(headerKey)
                .append(": ")
                .append(maskedHeadersSet.contains(headerKey) ? MASK_FILLER : String.join(", ", headerValues))
                .append('\n');
        });
        return sb.toString();
    }

    public static String toMaskedString(String queryParams, Collection<String> maskedQueryParams) {
        if (queryParams.isEmpty()) {
            return "";
        }
        if (maskedQueryParams.isEmpty()) {
            return queryParams;
        }

        final Set<String> maskedQueryParamsSet = toLowerCaseSet(maskedQueryParams);
        return Arrays.stream(queryParams.split("&"))
            .map(str -> {
                final int i = str.indexOf('=');
                if (i == -1) {
                    return str;
                }
                final String paramName = str.substring(0, i);
                if (maskedQueryParamsSet.contains(paramName.toLowerCase(Locale.ROOT))) {
                    return paramName + '=' + MASK_FILLER;
                } else {
                    return str;
                }
            })
            .collect(Collectors.joining("&"));
    }

    public static String toMaskedString(Map<String, ? extends Collection<String>> queryParams,
                                        Collection<String> maskedQueryParams) {
        if (queryParams.isEmpty()) {
            return "";
        }
        final Set<String> maskedQueryParamsSet = toLowerCaseSet(maskedQueryParams);
        return queryParams.entrySet().stream()
            .map(e -> {
                final String key = e.getKey();
                final Collection<String> values = e.getValue();
                if (maskedQueryParamsSet.contains(key.toLowerCase(Locale.ROOT))) {
                    return key + '=' + MASK_FILLER;
                } else {
                    if (values.isEmpty()) {
                        return key + '=';
                    } else {
                        return values.stream().map(v -> key + '=' + v).collect(Collectors.joining("&"));
                    }
                }
            }).collect(Collectors.joining("&"));
    }

    private static Set<String> toLowerCaseSet(Collection<String> collection) {
        return collection.stream()
            .map(e -> e.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
    }
}
