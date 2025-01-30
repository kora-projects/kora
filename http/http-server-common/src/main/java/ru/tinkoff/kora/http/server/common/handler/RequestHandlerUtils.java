package ru.tinkoff.kora.http.server.common.handler;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.cookie.Cookie;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponseException;

import java.util.*;

public final class RequestHandlerUtils {

    private RequestHandlerUtils() {
    }

    /*
     * Path: String, UUID, Integer, Long, Double
     */
    @Nonnull
    public static String parseStringPathParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.pathParams().get(name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Path parameter '%s' is required".formatted(name));
        }

        return decodeUrlSlashIfExist(result);
    }

    // %2F - / (slash)
    private static String decodeUrlSlashIfExist(String pathValue) {
        var encodedSymbolIndex = pathValue.indexOf('%');
        if (encodedSymbolIndex == -1) {
            return pathValue;
        }

        var lastEncodedSymbolIndex = 0;
        var builder = new StringBuilder(pathValue.length());
        var lengthLimit = pathValue.length() - 2;
        while (encodedSymbolIndex != -1 && (encodedSymbolIndex) < lengthLimit) {
            var isSlash = pathValue.charAt(encodedSymbolIndex + 1) == '2' && pathValue.charAt(encodedSymbolIndex + 2) == 'F';
            if (isSlash) {
                builder.append(pathValue, lastEncodedSymbolIndex, encodedSymbolIndex).append('/');
                lastEncodedSymbolIndex = encodedSymbolIndex + 3;
            }

            encodedSymbolIndex = pathValue.indexOf('%', encodedSymbolIndex + 1);
        }

        builder.append(pathValue.substring(lastEncodedSymbolIndex));
        return builder.toString();
    }

    @Nonnull
    public static UUID parseUUIDPathParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.pathParams().get(name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Path parameter '%s' is required".formatted(name));
        }

        try {
            return UUID.fromString(result);
        } catch (IllegalArgumentException e) {
            throw HttpServerResponseException.of(400, "Path parameter '%s' has invalid value '%s'".formatted(name, result));
        }
    }

    public static int parseIntegerPathParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.pathParams().get(name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Path parameter '%s' is required".formatted(name));
        }

        try {
            return Integer.parseInt(result);
        } catch (NumberFormatException e) {
            throw HttpServerResponseException.of(400, "Path parameter '%s' has invalid value '%s'".formatted(name, result));
        }
    }

    public static long parseLongPathParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.pathParams().get(name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Path parameter '%s' is required".formatted(name));
        }

        try {
            return Long.parseLong(result);
        } catch (NumberFormatException e) {
            throw HttpServerResponseException.of(400, "Path parameter %s(%s) has invalid value".formatted(name, result));
        }
    }

    public static double parseDoublePathParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.pathParams().get(name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Path parameter '%s' is required".formatted(name));
        }

        try {
            return Double.parseDouble(result);
        } catch (NumberFormatException e) {
            throw HttpServerResponseException.of(400, "Path parameter %s(%s) has invalid value".formatted(name, result));
        }
    }

    public static boolean parseBooleanPathParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.pathParams().get(name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Path parameter '%s' is required".formatted(name));
        }

        if ("true".equalsIgnoreCase(result)) {
            return true;
        } else if ("false".equalsIgnoreCase(result)) {
            return false;
        } else {
            throw HttpServerResponseException.of(400, "Path parameter %s(%s) has invalid value".formatted(name, result));
        }
    }

    /*
     * Headers: String, Integer, Long, Double, BigInteger, BigDecimal, UUID
     */
    @Nonnull
    public static String parseStringHeaderParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.headers().getAll(name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        }
        return String.join(", ", result);
    }

    @Nullable
    public static String parseOptionalStringHeaderParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.headers().getAll(name);
        if (result == null || result.isEmpty()) {
            return null;
        }
        return String.join(", ", result);
    }

    public static List<String> parseStringListHeaderParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseOptionalStringListHeaderParameter(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        }

        return result;
    }

    @Nullable
    public static List<String> parseOptionalStringListHeaderParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var headers = request.headers().getAll(name);
        if (headers == null) {
            return null;
        }

        List<String> result = new ArrayList<>();
        for (String header : headers) {
            String[] split = header.split(",");
            for (String s : split) {
                s = s.strip();
                if (!s.isBlank()) {
                    result.add(s);
                }
            }
        }

        return Collections.unmodifiableList(result);
    }

    public static Set<String> parseStringSetHeaderParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseOptionalStringSetHeaderParameter(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        }

        return result;
    }

    @Nullable
    public static Set<String> parseOptionalStringSetHeaderParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var headers = request.headers().getAll(name);
        if (headers == null) {
            return null;
        }

        Set<String> result = new LinkedHashSet<>();
        for (String header : headers) {
            String[] split = header.split(",");
            for (String s : split) {
                s = s.strip();
                if (!s.isBlank()) {
                    result.add(s);
                }
            }
        }

        return Collections.unmodifiableSet(result);
    }

    public static int parseIntegerHeaderParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.headers().getAll(name);
        if (result == null || result.isEmpty()) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        }

        var first = result.iterator().next().strip();
        if (first.isEmpty()) {
            throw HttpServerResponseException.of(400, "Header '%s' has invalid blank string value".formatted(name));
        }

        try {
            return Integer.parseInt(first);
        } catch (NumberFormatException e) {
            throw HttpServerResponseException.of(400, "Header %s(%s) has invalid value".formatted(name, first));
        }
    }

    @Nullable
    public static Integer parseOptionalIntegerHeaderParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.headers().getAll(name);
        if (result == null || result.isEmpty()) {
            return null;
        }

        var first = result.iterator().next().strip();
        if (first.isEmpty()) {
            throw HttpServerResponseException.of(400, "Header '%s' has invalid blank string value".formatted(name));
        }

        try {
            return Integer.parseInt(first);
        } catch (NumberFormatException e) {
            throw HttpServerResponseException.of(400, "Header %s(%s) has invalid value".formatted(name, first));
        }
    }

    public static List<Integer> parseIntegerListHeaderParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseOptionalIntegerListHeaderParameter(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static List<Integer> parseOptionalIntegerListHeaderParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var headers = request.headers().getAll(name);
        if (headers == null || headers.isEmpty()) {
            return null;
        }

        List<Integer> result = new ArrayList<>();
        for (String header : headers) {
            header = header.strip();
            if (!header.isEmpty()) {
                String[] split = header.split(",");
                for (String s : split) {
                    s = s.strip();
                    if (s.isEmpty()) {
                        throw HttpServerResponseException.of(400, "Header %s(%s) has invalid value".formatted(name, header));
                    }

                    try {
                        result.add(Integer.parseInt(s));
                    } catch (NumberFormatException e) {
                        throw HttpServerResponseException.of(400, "Header %s(%s) has invalid value".formatted(name, s));
                    }
                }
            }
        }

        return Collections.unmodifiableList(result);
    }

    public static Set<Integer> parseIntegerSetHeaderParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseOptionalIntegerSetHeaderParameter(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static Set<Integer> parseOptionalIntegerSetHeaderParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var headers = request.headers().getAll(name);
        if (headers == null || headers.isEmpty()) {
            return null;
        }

        Set<Integer> result = new LinkedHashSet<>();
        for (String header : headers) {
            header = header.strip();
            if (!header.isEmpty()) {
                String[] split = header.split(",");
                for (String s : split) {
                    s = s.strip();
                    if (s.isEmpty()) {
                        throw HttpServerResponseException.of(400, "Header %s(%s) has invalid value".formatted(name, header));
                    }

                    try {
                        result.add(Integer.parseInt(s));
                    } catch (NumberFormatException e) {
                        throw HttpServerResponseException.of(400, "Header %s(%s) has invalid value".formatted(name, s));
                    }
                }
            }
        }

        return Collections.unmodifiableSet(result);
    }

    public static long parseLongHeaderParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.headers().getAll(name);
        if (result == null || result.isEmpty()) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        }

        var first = result.iterator().next().strip();
        if (first.isEmpty()) {
            throw HttpServerResponseException.of(400, "Header '%s' has invalid blank string value".formatted(name));
        }

        try {
            return Long.parseLong(first);
        } catch (NumberFormatException e) {
            throw HttpServerResponseException.of(400, "Header %s(%s) has invalid value".formatted(name, first));
        }
    }

    @Nullable
    public static Long parseOptionalLongHeaderParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.headers().getAll(name);
        if (result == null || result.isEmpty()) {
            return null;
        }

        var first = result.iterator().next().strip();
        if (first.isEmpty()) {
            throw HttpServerResponseException.of(400, "Header '%s' has invalid blank string value".formatted(name));
        }

        try {
            return Long.parseLong(first);
        } catch (NumberFormatException e) {
            throw HttpServerResponseException.of(400, "Header %s(%s) has invalid value".formatted(name, first));
        }
    }

    public static List<Long> parseLongListHeaderParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseOptionalLongListHeaderParameter(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static List<Long> parseOptionalLongListHeaderParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var headers = request.headers().getAll(name);
        if (headers == null || headers.isEmpty()) {
            return null;
        }

        List<Long> result = new ArrayList<>();
        for (String header : headers) {
            header = header.strip();
            if (!header.isEmpty()) {
                String[] split = header.split(",");
                for (String s : split) {
                    s = s.strip();
                    if (s.isEmpty()) {
                        throw HttpServerResponseException.of(400, "Header %s(%s) has invalid value".formatted(name, header));
                    }

                    try {
                        result.add(Long.parseLong(s));
                    } catch (NumberFormatException e) {
                        throw HttpServerResponseException.of(400, "Header %s(%s) has invalid value".formatted(name, s));
                    }
                }
            }
        }

        return Collections.unmodifiableList(result);
    }

    public static Set<Long> parseLongSetHeaderParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseOptionalLongSetHeaderParameter(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static Set<Long> parseOptionalLongSetHeaderParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var headers = request.headers().getAll(name);
        if (headers == null || headers.isEmpty()) {
            return null;
        }

        Set<Long> result = new LinkedHashSet<>();
        for (String header : headers) {
            header = header.strip();
            if (!header.isEmpty()) {
                String[] split = header.split(",");
                for (String s : split) {
                    s = s.strip();
                    if (s.isEmpty()) {
                        throw HttpServerResponseException.of(400, "Header %s(%s) has invalid value".formatted(name, header));
                    }

                    try {
                        result.add(Long.parseLong(s));
                    } catch (NumberFormatException e) {
                        throw HttpServerResponseException.of(400, "Header %s(%s) has invalid value".formatted(name, s));
                    }
                }
            }
        }

        return Collections.unmodifiableSet(result);
    }

    public static double parseDoubleHeaderParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.headers().getAll(name);
        if (result == null || result.isEmpty()) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        }

        var first = result.iterator().next().strip();
        if (first.isEmpty()) {
            throw HttpServerResponseException.of(400, "Header '%s' has invalid blank string value".formatted(name));
        }

        try {
            return Double.parseDouble(first);
        } catch (NumberFormatException e) {
            throw HttpServerResponseException.of(400, "Header %s(%s) has invalid value".formatted(name, first));
        }
    }

    @Nullable
    public static Double parseOptionalDoubleHeaderParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.headers().getAll(name);
        if (result == null || result.isEmpty()) {
            return null;
        }

        var first = result.iterator().next().strip();
        if (first.isEmpty()) {
            throw HttpServerResponseException.of(400, "Header '%s' has invalid blank string value".formatted(name));
        }

        try {
            return Double.parseDouble(first);
        } catch (NumberFormatException e) {
            throw HttpServerResponseException.of(400, "Header %s(%s) has invalid value".formatted(name, first));
        }
    }

    public static List<Double> parseDoubleListHeaderParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseOptionalDoubleListHeaderParameter(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static List<Double> parseOptionalDoubleListHeaderParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var headers = request.headers().getAll(name);
        if (headers == null || headers.isEmpty()) {
            return null;
        }

        List<Double> result = new ArrayList<>();
        for (String header : headers) {
            header = header.strip();
            if (!header.isEmpty()) {
                String[] split = header.split(",");
                for (String s : split) {
                    s = s.strip();
                    if (s.isEmpty()) {
                        throw HttpServerResponseException.of(400, "Header %s(%s) has invalid value".formatted(name, header));
                    }

                    try {
                        result.add(Double.parseDouble(s));
                    } catch (NumberFormatException e) {
                        throw HttpServerResponseException.of(400, "Header %s(%s) has invalid value".formatted(name, s));
                    }
                }
            }
        }

        return Collections.unmodifiableList(result);
    }

    public static Set<Double> parseDoubleSetHeaderParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseOptionalDoubleSetHeaderParameter(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static Set<Double> parseOptionalDoubleSetHeaderParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var headers = request.headers().getAll(name);
        if (headers == null || headers.isEmpty()) {
            return null;
        }

        Set<Double> result = new LinkedHashSet<>();
        for (String header : headers) {
            header = header.strip();
            if (!header.isEmpty()) {
                String[] split = header.split(",");
                for (String s : split) {
                    s = s.strip();
                    if (s.isEmpty()) {
                        throw HttpServerResponseException.of(400, "Header %s(%s) has invalid value".formatted(name, header));
                    }

                    try {
                        result.add(Double.parseDouble(s));
                    } catch (NumberFormatException e) {
                        throw HttpServerResponseException.of(400, "Header %s(%s) has invalid value".formatted(name, s));
                    }
                }
            }
        }

        return Collections.unmodifiableSet(result);
    }

    public static UUID parseUuidHeaderParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseOptionalUuidHeaderParameter(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        } else {
            return result;
        }
    }

    @Nullable
    public static UUID parseOptionalUuidHeaderParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.headers().getAll(name);
        if (result == null || result.isEmpty()) {
            return null;
        }

        var first = result.iterator().next().strip();
        if (first.isEmpty()) {
            throw HttpServerResponseException.of(400, "Header '%s' has invalid blank string value".formatted(name));
        }

        try {
            return UUID.fromString(first);
        } catch (NumberFormatException e) {
            throw HttpServerResponseException.of(400, "Header %s(%s) has invalid value".formatted(name, first));
        }
    }

    public static List<UUID> parseUuidListHeaderParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseOptionalUuidListHeaderParameter(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static List<UUID> parseOptionalUuidListHeaderParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var headers = request.headers().getAll(name);
        if (headers == null || headers.isEmpty()) {
            return null;
        }

        List<UUID> result = new ArrayList<>();
        for (String header : headers) {
            header = header.strip();
            if (!header.isEmpty()) {
                String[] split = header.split(",");
                for (String s : split) {
                    s = s.strip();
                    if (s.isEmpty()) {
                        throw HttpServerResponseException.of(400, "Header %s(%s) has invalid value".formatted(name, header));
                    }

                    try {
                        result.add(UUID.fromString(s));
                    } catch (NumberFormatException e) {
                        throw HttpServerResponseException.of(400, "Header %s(%s) has invalid value".formatted(name, s));
                    }
                }
            }
        }

        return Collections.unmodifiableList(result);
    }

    public static Set<UUID> parseUuidSetHeaderParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseOptionalUuidSetHeaderParameter(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static Set<UUID> parseOptionalUuidSetHeaderParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var headers = request.headers().getAll(name);
        if (headers == null || headers.isEmpty()) {
            return null;
        }

        Set<UUID> result = new LinkedHashSet<>();
        for (String header : headers) {
            header = header.strip();
            if (!header.isEmpty()) {
                String[] split = header.split(",");
                for (String s : split) {
                    s = s.strip();
                    if (s.isEmpty()) {
                        throw HttpServerResponseException.of(400, "Header %s(%s) has invalid value".formatted(name, header));
                    }

                    try {
                        result.add(UUID.fromString(s));
                    } catch (NumberFormatException e) {
                        throw HttpServerResponseException.of(400, "Header %s(%s) has invalid value".formatted(name, s));
                    }
                }
            }
        }

        return Collections.unmodifiableSet(result);
    }

    public static <T> List<T> parseSomeListHeaderParameter(HttpServerRequest request, String name, StringParameterReader<T> mapping) throws HttpServerResponseException {
        var result = parseOptionalSomeListHeaderParameter(request, name, mapping);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static <T> List<T> parseOptionalSomeListHeaderParameter(HttpServerRequest request, String name, StringParameterReader<T> mapping) throws HttpServerResponseException {
        var headers = request.headers().getAll(name);
        if (headers == null || headers.isEmpty()) {
            return null;
        }

        List<T> result = new ArrayList<>();
        for (String header : headers) {
            header = header.strip();
            if (!header.isEmpty()) {
                String[] split = header.split(",");
                for (String s : split) {
                    s = s.strip();
                    if (s.isEmpty()) {
                        throw HttpServerResponseException.of(400, "Header %s(%s) has invalid value".formatted(name, header));
                    }

                    try {
                        T value = mapping.read(s);
                        result.add(value);
                    } catch (HttpServerResponseException e) {
                        throw HttpServerResponseException.of(400, "Header %s(%s) has invalid value".formatted(name, s));
                    } catch (Exception e) {
                        throw HttpServerResponseException.of(400, "Header %s(%s) has invalid value due to: ".formatted(name, s) + e.getMessage());
                    }
                }
            }
        }

        return Collections.unmodifiableList(result);
    }

    public static <T> Set<T> parseSomeSetHeaderParameter(HttpServerRequest request, String name, StringParameterReader<T> mapping) throws HttpServerResponseException {
        var result = parseOptionalSomeSetHeaderParameter(request, name, mapping);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static <T> Set<T> parseOptionalSomeSetHeaderParameter(HttpServerRequest request, String name, StringParameterReader<T> mapping) throws HttpServerResponseException {
        var headers = request.headers().getAll(name);
        if (headers == null || headers.isEmpty()) {
            return null;
        }

        Set<T> result = new LinkedHashSet<>();
        for (String header : headers) {
            header = header.strip();
            if (!header.isEmpty()) {
                String[] split = header.split(",");
                for (String s : split) {
                    s = s.strip();
                    if (s.isEmpty()) {
                        throw HttpServerResponseException.of(400, "Header %s(%s) has invalid value".formatted(name, header));
                    }

                    try {
                        T value = mapping.read(s);
                        result.add(value);
                    } catch (HttpServerResponseException e) {
                        throw HttpServerResponseException.of(400, "Header %s(%s) has invalid value".formatted(name, s));
                    } catch (Exception e) {
                        throw HttpServerResponseException.of(400, "Header %s(%s) has invalid value due to: ".formatted(name, s) + e.getMessage());
                    }
                }
            }
        }

        return Collections.unmodifiableSet(result);
    }

    /*
     * Query: String, Integer, Long, Double, Boolean, UUID
     */
    @Nonnull
    public static UUID parseUuidQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseOptionalUuidQueryParameter(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }

        return result;
    }

    @Nullable
    public static UUID parseOptionalUuidQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.queryParams().get(name);
        if (result == null || result.isEmpty()) {
            return null;
        }

        var first = result.iterator().next().strip();
        if (first.isEmpty()) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
        }

        try {
            return UUID.fromString(first);
        } catch (IllegalArgumentException e) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid value '%s'".formatted(name, result));
        }
    }

    @Nonnull
    public static String parseStringQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseOptionalStringQueryParameter(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }

        return result;
    }

    @Nullable
    public static String parseOptionalStringQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.queryParams().get(name);
        if (result == null || result.isEmpty()) {
            return null;
        }

        return result.iterator().next();
    }

    public static int parseIntegerQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseOptionalIntegerQueryParameter(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static Integer parseOptionalIntegerQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.queryParams().get(name);
        if (result == null || result.isEmpty()) {
            return null;
        }

        var first = result.iterator().next().strip();
        if (first.isEmpty()) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
        }

        try {
            return Integer.parseInt(first);
        } catch (NumberFormatException e) {
            throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".formatted(name, first));
        }
    }

    public static long parseLongQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseOptionalLongQueryParameter(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static Long parseOptionalLongQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.queryParams().get(name);
        if (result == null || result.isEmpty()) {
            return null;
        }

        var first = result.iterator().next().strip();
        if (first.isEmpty()) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
        }

        try {
            return Long.parseLong(first);
        } catch (NumberFormatException e) {
            throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".formatted(name, first));
        }
    }

    public static boolean parseBooleanQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseOptionalBooleanQueryParameter(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static Boolean parseOptionalBooleanQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.queryParams().get(name);
        if (result == null || result.isEmpty()) {
            return null;
        }

        var first = result.iterator().next().strip();
        if (first.isEmpty()) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
        }

        if ("true".equalsIgnoreCase(first)) {
            return true;
        } else if ("false".equalsIgnoreCase(first)) {
            return false;
        } else {
            throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".formatted(name, result));
        }
    }

    public static double parseDoubleQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseOptionalDoubleQueryParameter(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static Double parseOptionalDoubleQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.queryParams().get(name);
        if (result == null || result.isEmpty()) {
            return null;
        }

        var first = result.iterator().next().strip();
        if (first.isEmpty()) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
        }

        try {
            return Double.parseDouble(first);
        } catch (NumberFormatException e) {
            throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".formatted(name, first));
        }
    }

    /*
     * Query: List<String>, List<Integer>, List<Long>, List<Double>, List<Boolean>, List<UUID>
     */
    @Nonnull
    public static List<Integer> parseIntegerListQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseOptionalIntegerListQueryParameter(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static List<Integer> parseOptionalIntegerListQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.queryParams().get(name);
        if (result == null) {
            return null;
        }
        var list = new ArrayList<Integer>(result.size());
        for (var str : result) {
            if (str != null) {
                str = str.strip();
                if (str.isEmpty()) {
                    throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
                }
                try {
                    list.add(Integer.parseInt(str));
                } catch (NumberFormatException e) {
                    throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".formatted(name, str));
                }
            }
        }
        return list;
    }

    @Nonnull
    public static List<UUID> parseUuidListQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseOptionalUuidListQueryParameter(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static List<UUID> parseOptionalUuidListQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.queryParams().get(name);
        if (result == null) {
            return null;
        }

        var list = new ArrayList<UUID>(result.size());
        for (var str : result) {
            if (str != null) {
                str = str.strip();
                if (str.isEmpty()) {
                    throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
                }
                try {
                    list.add(UUID.fromString(str));
                } catch (NumberFormatException e) {
                    throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".formatted(name, str));
                }
            }
        }
        return list;
    }

    @Nonnull
    public static List<String> parseStringListQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseOptionalStringListQueryParameter(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static List<String> parseOptionalStringListQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.queryParams().get(name);
        if (result == null) {
            return null;
        }

        return result.stream().toList();
    }

    @Nonnull
    public static List<Long> parseLongListQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseOptionalLongListQueryParameter(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static List<Long> parseOptionalLongListQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.queryParams().get(name);
        if (result == null) {
            return null;
        }

        var list = new ArrayList<Long>(result.size());
        for (var str : result) {
            if (str != null) {
                str = str.strip();
                if (str.isEmpty()) {
                    throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
                }
                try {
                    list.add(Long.parseLong(str));
                } catch (NumberFormatException e) {
                    throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".formatted(name, str));
                }
            }
        }
        return list;
    }

    @Nonnull
    public static List<Double> parseDoubleListQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseOptionalDoubleListQueryParameter(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static List<Double> parseOptionalDoubleListQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.queryParams().get(name);
        if (result == null) {
            return null;
        }

        var list = new ArrayList<Double>(result.size());
        for (var str : result) {
            if (str != null) {
                str = str.strip();
                if (str.isEmpty()) {
                    throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
                }
                try {
                    list.add(Double.parseDouble(str));
                } catch (NumberFormatException e) {
                    throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".formatted(name, str));
                }
            }
        }
        return list;
    }

    @Nonnull
    public static List<Boolean> parseBooleanListQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseOptionalBooleanListQueryParameter(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static List<Boolean> parseOptionalBooleanListQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.queryParams().get(name);
        if (result == null) {
            return null;
        }

        var list = new ArrayList<Boolean>(result.size());
        for (var str : result) {
            if (str != null) {
                str = str.strip();
                if (str.isEmpty()) {
                    throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
                }
                if ("true".equalsIgnoreCase(str)) {
                    list.add(true);
                } else if ("false".equalsIgnoreCase(str)) {
                    list.add(false);
                } else {
                    throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".formatted(name, result));
                }
            }
        }
        return list;
    }

    @Nonnull
    public static Set<Integer> parseIntegerSetQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseOptionalIntegerSetQueryParameter(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static Set<Integer> parseOptionalIntegerSetQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.queryParams().get(name);
        if (result == null) {
            return null;
        }
        var set = new LinkedHashSet<Integer>(result.size() + 1);
        for (var str : result) {
            if (str != null) {
                str = str.strip();
                if (str.isEmpty()) {
                    throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
                }
                try {
                    set.add(Integer.parseInt(str));
                } catch (NumberFormatException e) {
                    throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".formatted(name, str));
                }
            }
        }
        return Collections.unmodifiableSet(set);
    }

    @Nonnull
    public static Set<UUID> parseUuidSetQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseOptionalUuidSetQueryParameter(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static Set<UUID> parseOptionalUuidSetQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.queryParams().get(name);
        if (result == null) {
            return null;
        }

        var set = new LinkedHashSet<UUID>(result.size() + 1);
        for (var str : result) {
            if (str != null) {
                str = str.strip();
                if (str.isEmpty()) {
                    throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
                }
                try {
                    set.add(UUID.fromString(str));
                } catch (NumberFormatException e) {
                    throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".formatted(name, str));
                }
            }
        }
        return Collections.unmodifiableSet(set);
    }

    @Nonnull
    public static Set<String> parseStringSetQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseOptionalStringSetQueryParameter(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static Set<String> parseOptionalStringSetQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.queryParams().get(name);
        if (result == null) {
            return null;
        }

        var set = new LinkedHashSet<String>(result.size() + 1);
        for (var str : result) {
            if (str != null) {
                set.add(str);
            }
        }

        return Collections.unmodifiableSet(set);
    }

    @Nonnull
    public static Set<Long> parseLongSetQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseOptionalLongSetQueryParameter(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static Set<Long> parseOptionalLongSetQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.queryParams().get(name);
        if (result == null) {
            return null;
        }

        var set = new LinkedHashSet<Long>(result.size() + 1);
        for (var str : result) {
            if (str != null) {
                str = str.strip();
                if (str.isEmpty()) {
                    throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
                }
                try {
                    set.add(Long.parseLong(str));
                } catch (NumberFormatException e) {
                    throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".formatted(name, str));
                }
            }
        }
        return Collections.unmodifiableSet(set);
    }

    @Nonnull
    public static Set<Double> parseDoubleSetQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseOptionalDoubleSetQueryParameter(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static Set<Double> parseOptionalDoubleSetQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.queryParams().get(name);
        if (result == null) {
            return null;
        }

        var set = new LinkedHashSet<Double>(result.size() + 1);
        for (var str : result) {
            if (str != null) {
                str = str.strip();
                if (str.isEmpty()) {
                    throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
                }
                try {
                    set.add(Double.parseDouble(str));
                } catch (NumberFormatException e) {
                    throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".formatted(name, str));
                }
            }
        }
        return Collections.unmodifiableSet(set);
    }

    @Nonnull
    public static Set<Boolean> parseBooleanSetQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseOptionalBooleanSetQueryParameter(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static Set<Boolean> parseOptionalBooleanSetQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.queryParams().get(name);
        if (result == null) {
            return null;
        }

        var set = new LinkedHashSet<Boolean>(result.size() + 1);
        for (var str : result) {
            if (str != null) {
                str = str.strip();
                if (str.isEmpty()) {
                    throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
                }

                if ("true".equalsIgnoreCase(str)) {
                    set.add(true);
                } else if ("false".equalsIgnoreCase(str)) {
                    set.add(false);
                } else {
                    throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".formatted(name, result));
                }
            }
        }
        return Collections.unmodifiableSet(set);
    }

    public static <T> List<T> parseSomeListQueryParameter(HttpServerRequest request, String name, StringParameterReader<T> mapping) throws HttpServerResponseException {
        var result = parseOptionalSomeListQueryParameter(request, name, mapping);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static <T> List<T> parseOptionalSomeListQueryParameter(HttpServerRequest request, String name, StringParameterReader<T> mapping) throws HttpServerResponseException {
        var headers = request.headers().getAll(name);
        if (headers == null || headers.isEmpty()) {
            return null;
        }

        List<T> result = new ArrayList<>();
        for (String header : headers) {
            header = header.strip();
            if (!header.isEmpty()) {
                String[] split = header.split(",");
                for (String s : split) {
                    s = s.strip();
                    if (s.isEmpty()) {
                        throw HttpServerResponseException.of(400, "Query %s(%s) has invalid value".formatted(name, header));
                    }

                    try {
                        T value = mapping.read(s);
                        result.add(value);
                    } catch (HttpServerResponseException e) {
                        throw HttpServerResponseException.of(400, "Query %s(%s) has invalid value".formatted(name, s));
                    } catch (Exception e) {
                        throw HttpServerResponseException.of(400, "Query %s(%s) has invalid value due to: ".formatted(name, s) + e.getMessage());
                    }
                }
            }
        }

        return Collections.unmodifiableList(result);
    }

    public static <T> Set<T> parseSomeSetQueryParameter(HttpServerRequest request, String name, StringParameterReader<T> mapping) throws HttpServerResponseException {
        var result = parseOptionalSomeSetQueryParameter(request, name, mapping);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static <T> Set<T> parseOptionalSomeSetQueryParameter(HttpServerRequest request, String name, StringParameterReader<T> mapping) throws HttpServerResponseException {
        var headers = request.headers().getAll(name);
        if (headers == null || headers.isEmpty()) {
            return null;
        }

        Set<T> result = new LinkedHashSet<>();
        for (String header : headers) {
            header = header.strip();
            if (!header.isEmpty()) {
                String[] split = header.split(",");
                for (String s : split) {
                    s = s.strip();
                    if (s.isEmpty()) {
                        throw HttpServerResponseException.of(400, "Query %s(%s) has invalid value".formatted(name, header));
                    }

                    try {
                        T value = mapping.read(s);
                        result.add(value);
                    } catch (HttpServerResponseException e) {
                        throw HttpServerResponseException.of(400, "Query %s(%s) has invalid value".formatted(name, s));
                    } catch (Exception e) {
                        throw HttpServerResponseException.of(400, "Query %s(%s) has invalid value due to: ".formatted(name, s) + e.getMessage());
                    }
                }
            }
        }

        return Collections.unmodifiableSet(result);
    }

    // cookies
    @Nullable
    public static Cookie parseOptionalCookie(HttpServerRequest request, String name) {
        var cookies = request.cookies();
        for (var cookie : cookies) {
            if (Objects.equals(cookie.name(), name)) {
                return cookie;
            }
        }
        return null;
    }

    public static Cookie parseCookie(HttpServerRequest request, String name) {
        var result = parseOptionalCookie(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Cookie '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static String parseOptionalCookieString(HttpServerRequest request, String name) {
        var cookie = parseOptionalCookie(request, name);
        if (cookie != null) {
            return cookie.value();
        }
        return null;
    }

    public static String parseCookieString(HttpServerRequest request, String name) {
        var result = parseOptionalCookieString(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Cookie '%s' is required".formatted(name));
        }
        return result;
    }
}
