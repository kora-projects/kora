package io.koraframework.http.server.common.request;

import org.jspecify.annotations.Nullable;
import io.koraframework.http.common.cookie.Cookie;
import io.koraframework.http.server.common.response.HttpServerResponseException;

import java.util.*;

public final class HttpRequestHandlerUtils {

    private HttpRequestHandlerUtils() {}

    private interface HeaderValueConsumer {
        void accept(String value);
    }

    private static void forEachCommaSeparatedHeaderValue(String header, HeaderValueConsumer consumer) {
        forEachCommaSeparatedHeaderValue(header, true, consumer);
    }

    private static void forEachCommaSeparatedRawHeaderValue(String header, HeaderValueConsumer consumer) {
        forEachCommaSeparatedHeaderValue(header, false, consumer);
    }

    private static void forEachCommaSeparatedHeaderValue(String header, boolean strip, HeaderValueConsumer consumer) {
        var start = 0;
        while (start <= header.length()) {
            var end = header.indexOf(',', start);
            if (end == -1) {
                end = header.length();
            }
            var value = header.substring(start, end);
            consumer.accept(strip ? value.strip() : value);
            if (end == header.length()) {
                return;
            }
            start = end + 1;
        }
    }

    private static int calculateHashSetCapacity(int size) {
        return Math.max((int) (size / 0.75f) + 1, 16);
    }

    /*
     * Path: String, UUID, Integer, Long, Double
     */
    public static String parsePathString(HttpServerRequest request, String name) throws HttpServerResponseException {
        var param = request.pathParams().get(name);
        if (param == null) {
            throw HttpServerResponseException.of(400, "Path parameter '%s' is required".formatted(name));
        }

        return decodeUrlSlashIfExist(param);
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

    public static UUID parsePathUuid(HttpServerRequest request, String name) throws HttpServerResponseException {
        var param = request.pathParams().get(name);
        if (param == null) {
            throw HttpServerResponseException.of(400, "Path parameter '%s' is required".formatted(name));
        }

        try {
            return UUID.fromString(param);
        } catch (IllegalArgumentException e) {
            throw HttpServerResponseException.of(400, "Path parameter '%s' has invalid value: %s".formatted(name, param));
        }
    }

    public static int parsePathInteger(HttpServerRequest request, String name) throws HttpServerResponseException {
        var param = request.pathParams().get(name);
        if (param == null) {
            throw HttpServerResponseException.of(400, "Path parameter '%s' is required".formatted(name));
        }

        try {
            return Integer.parseInt(param);
        } catch (NumberFormatException e) {
            throw HttpServerResponseException.of(400, "Path parameter '%s' has invalid value: %s".formatted(name, param));
        }
    }

    public static long parsePathLong(HttpServerRequest request, String name) throws HttpServerResponseException {
        var param = request.pathParams().get(name);
        if (param == null) {
            throw HttpServerResponseException.of(400, "Path parameter '%s' is required".formatted(name));
        }

        try {
            return Long.parseLong(param);
        } catch (NumberFormatException e) {
            throw HttpServerResponseException.of(400, "Path parameter '%s' has invalid value: %s".formatted(name, param));
        }
    }

    public static double parsePathDouble(HttpServerRequest request, String name) throws HttpServerResponseException {
        var param = request.pathParams().get(name);
        if (param == null) {
            throw HttpServerResponseException.of(400, "Path parameter '%s' is required".formatted(name));
        }

        try {
            return Double.parseDouble(param);
        } catch (NumberFormatException e) {
            throw HttpServerResponseException.of(400, "Path parameter '%s' has invalid value: %s".formatted(name, param));
        }
    }

    public static boolean parsePathBoolean(HttpServerRequest request, String name) throws HttpServerResponseException {
        var param = request.pathParams().get(name);
        if (param == null) {
            throw HttpServerResponseException.of(400, "Path parameter '%s' is required".formatted(name));
        }

        if ("true".equalsIgnoreCase(param)) {
            return true;
        } else if ("false".equalsIgnoreCase(param)) {
            return false;
        } else {
            throw HttpServerResponseException.of(400, "Path parameter '%s' has invalid value: %s".formatted(name, param));
        }
    }

    /*
     * Headers: String, Integer, Long, Double, BigInteger, BigDecimal, UUID
     */
    public static String parseHeaderString(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.headers().getAll(name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        }
        return String.join(", ", result);
    }

    @Nullable
    public static String parseHeaderStringNullable(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.headers().getAll(name);
        if (result == null || result.isEmpty()) {
            return null;
        }
        return String.join(", ", result);
    }

    public static List<String> parseHeaderStringList(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseHeaderStringListNullable(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        }

        return result;
    }

    @Nullable
    public static List<String> parseHeaderStringListNullable(HttpServerRequest request, String name) throws HttpServerResponseException {
        var headers = request.headers().getAll(name);
        if (headers == null) {
            return null;
        } else if (headers.isEmpty()) {
            return List.of();
        }

        List<String> result = new ArrayList<>(headers.size());
        for (String header : headers) {
            forEachCommaSeparatedHeaderValue(header, s -> {
                if (!s.isBlank()) {
                    result.add(s);
                }
            });
        }

        return Collections.unmodifiableList(result);
    }

    public static Set<String> parseHeaderStringSet(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseHeaderStringSetNullable(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        }

        return result;
    }

    @Nullable
    public static Set<String> parseHeaderStringSetNullable(HttpServerRequest request, String name) throws HttpServerResponseException {
        var headers = request.headers().getAll(name);
        if (headers == null) {
            return null;
        } else if (headers.isEmpty()) {
            return Set.of();
        }

        Set<String> result = new LinkedHashSet<>(calculateHashSetCapacity(headers.size()));
        for (String header : headers) {
            forEachCommaSeparatedHeaderValue(header, s -> {
                if (!s.isBlank()) {
                    result.add(s);
                }
            });
        }

        return Collections.unmodifiableSet(result);
    }

    public static int parseHeaderInteger(HttpServerRequest request, String name) throws HttpServerResponseException {
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
            throw HttpServerResponseException.of(400, "Header '%s' has invalid value: %s".formatted(name, first));
        }
    }

    @Nullable
    public static Integer parseHeaderIntegerNullable(HttpServerRequest request, String name) throws HttpServerResponseException {
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
            throw HttpServerResponseException.of(400, "Header '%s' has invalid value: %s".formatted(name, first));
        }
    }

    public static List<Integer> parseHeaderIntegerList(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseHeaderIntegerListNullable(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static List<Integer> parseHeaderIntegerListNullable(HttpServerRequest request, String name) throws HttpServerResponseException {
        var headers = request.headers().getAll(name);
        if (headers == null) {
            return null;
        } else if (headers.isEmpty()) {
            return List.of();
        }

        List<Integer> result = new ArrayList<>(headers.size());
        for (String header : headers) {
            var strippedHeader = header.strip();
            if (!strippedHeader.isEmpty()) {
                forEachCommaSeparatedHeaderValue(strippedHeader, s -> {
                    if (s.isEmpty()) {
                        throw HttpServerResponseException.of(400, "Header '%s' has invalid value: %s".formatted(name, strippedHeader));
                    }

                    try {
                        result.add(Integer.parseInt(s));
                    } catch (NumberFormatException e) {
                        throw HttpServerResponseException.of(400, "Header '%s' has invalid value: %s".formatted(name, s));
                    }
                });
            }
        }

        return Collections.unmodifiableList(result);
    }

    public static Set<Integer> parseHeaderIntegerSet(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseHeaderIntegerSetNullable(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static Set<Integer> parseHeaderIntegerSetNullable(HttpServerRequest request, String name) throws HttpServerResponseException {
        var headers = request.headers().getAll(name);
        if (headers == null) {
            return null;
        } else if (headers.isEmpty()) {
            return Set.of();
        }

        Set<Integer> result = new LinkedHashSet<>(calculateHashSetCapacity(headers.size()));
        for (String header : headers) {
            var strippedHeader = header.strip();
            if (!strippedHeader.isEmpty()) {
                forEachCommaSeparatedHeaderValue(strippedHeader, s -> {
                    if (s.isEmpty()) {
                        throw HttpServerResponseException.of(400, "Header '%s' has invalid value: %s".formatted(name, strippedHeader));
                    }

                    try {
                        result.add(Integer.parseInt(s));
                    } catch (NumberFormatException e) {
                        throw HttpServerResponseException.of(400, "Header '%s' has invalid value: %s".formatted(name, s));
                    }
                });
            }
        }

        return Collections.unmodifiableSet(result);
    }

    public static long parseHeaderLong(HttpServerRequest request, String name) throws HttpServerResponseException {
        var param = request.headers().getAll(name);
        if (param == null || param.isEmpty()) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        }

        var first = param.iterator().next().strip();
        if (first.isEmpty()) {
            throw HttpServerResponseException.of(400, "Header '%s' has invalid blank string value".formatted(name));
        }

        try {
            return Long.parseLong(first);
        } catch (NumberFormatException e) {
            throw HttpServerResponseException.of(400, "Header '%s' has invalid value: %s".formatted(name, first));
        }
    }

    @Nullable
    public static Long parseHeaderLongNullable(HttpServerRequest request, String name) throws HttpServerResponseException {
        var param = request.headers().getAll(name);
        if (param == null || param.isEmpty()) {
            return null;
        }

        var first = param.iterator().next().strip();
        if (first.isEmpty()) {
            throw HttpServerResponseException.of(400, "Header '%s' has invalid blank string value".formatted(name));
        }

        try {
            return Long.parseLong(first);
        } catch (NumberFormatException e) {
            throw HttpServerResponseException.of(400, "Header '%s' has invalid value: %s".formatted(name, first));
        }
    }

    public static List<Long> parseHeaderLongList(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseHeaderLongListNullable(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static List<Long> parseHeaderLongListNullable(HttpServerRequest request, String name) throws HttpServerResponseException {
        var headers = request.headers().getAll(name);
        if (headers == null) {
            return null;
        } else if (headers.isEmpty()) {
            return List.of();
        }

        List<Long> result = new ArrayList<>(headers.size());
        for (String header : headers) {
            var strippedHeader = header.strip();
            if (!strippedHeader.isEmpty()) {
                forEachCommaSeparatedHeaderValue(strippedHeader, s -> {
                    if (s.isEmpty()) {
                        throw HttpServerResponseException.of(400, "Header '%s' has invalid value: %s".formatted(name, strippedHeader));
                    }

                    try {
                        result.add(Long.parseLong(s));
                    } catch (NumberFormatException e) {
                        throw HttpServerResponseException.of(400, "Header '%s' has invalid value: %s".formatted(name, s));
                    }
                });
            }
        }

        return Collections.unmodifiableList(result);
    }

    public static Set<Long> parseHeaderLongSet(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseHeaderLongSetNullable(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static Set<Long> parseHeaderLongSetNullable(HttpServerRequest request, String name) throws HttpServerResponseException {
        var headers = request.headers().getAll(name);
        if (headers == null) {
            return null;
        } else if (headers.isEmpty()) {
            return Set.of();
        }

        Set<Long> result = new LinkedHashSet<>(calculateHashSetCapacity(headers.size()));
        for (String header : headers) {
            var strippedHeader = header.strip();
            if (!strippedHeader.isEmpty()) {
                forEachCommaSeparatedHeaderValue(strippedHeader, s -> {
                    if (s.isEmpty()) {
                        throw HttpServerResponseException.of(400, "Header '%s' has invalid value: %s".formatted(name, strippedHeader));
                    }

                    try {
                        result.add(Long.parseLong(s));
                    } catch (NumberFormatException e) {
                        throw HttpServerResponseException.of(400, "Header '%s' has invalid value: %s".formatted(name, s));
                    }
                });
            }
        }

        return Collections.unmodifiableSet(result);
    }

    public static double parseHeaderDouble(HttpServerRequest request, String name) throws HttpServerResponseException {
        var param = request.headers().getAll(name);
        if (param == null || param.isEmpty()) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        }

        var first = param.iterator().next().strip();
        if (first.isEmpty()) {
            throw HttpServerResponseException.of(400, "Header '%s' has invalid blank string value".formatted(name));
        }

        try {
            return Double.parseDouble(first);
        } catch (NumberFormatException e) {
            throw HttpServerResponseException.of(400, "Header '%s' has invalid value: %s".formatted(name, first));
        }
    }

    @Nullable
    public static Double parseHeaderDoubleNullable(HttpServerRequest request, String name) throws HttpServerResponseException {
        var param = request.headers().getAll(name);
        if (param == null || param.isEmpty()) {
            return null;
        }

        var first = param.iterator().next().strip();
        if (first.isEmpty()) {
            throw HttpServerResponseException.of(400, "Header '%s' has invalid blank string value".formatted(name));
        }

        try {
            return Double.parseDouble(first);
        } catch (NumberFormatException e) {
            throw HttpServerResponseException.of(400, "Header '%s' has invalid value: %s".formatted(name, first));
        }
    }

    public static List<Double> parseHeaderDoubleList(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseHeaderDoubleListNullable(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static List<Double> parseHeaderDoubleListNullable(HttpServerRequest request, String name) throws HttpServerResponseException {
        var headers = request.headers().getAll(name);
        if (headers == null) {
            return null;
        } else if (headers.isEmpty()) {
            return List.of();
        }

        List<Double> result = new ArrayList<>(headers.size());
        for (String header : headers) {
            var strippedHeader = header.strip();
            if (!strippedHeader.isEmpty()) {
                forEachCommaSeparatedHeaderValue(strippedHeader, s -> {
                    if (s.isEmpty()) {
                        throw HttpServerResponseException.of(400, "Header '%s' has invalid value: %s".formatted(name, strippedHeader));
                    }

                    try {
                        result.add(Double.parseDouble(s));
                    } catch (NumberFormatException e) {
                        throw HttpServerResponseException.of(400, "Header '%s' has invalid value: %s".formatted(name, s));
                    }
                });
            }
        }

        return Collections.unmodifiableList(result);
    }

    public static Set<Double> parseHeaderDoubleSet(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseHeaderDoubleSetNullable(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static Set<Double> parseHeaderDoubleSetNullable(HttpServerRequest request, String name) throws HttpServerResponseException {
        var headers = request.headers().getAll(name);
        if (headers == null) {
            return null;
        } else if (headers.isEmpty()) {
            return Set.of();
        }

        Set<Double> result = new LinkedHashSet<>(calculateHashSetCapacity(headers.size()));
        for (String header : headers) {
            var strippedHeader = header.strip();
            if (!strippedHeader.isEmpty()) {
                forEachCommaSeparatedHeaderValue(strippedHeader, s -> {
                    if (s.isEmpty()) {
                        throw HttpServerResponseException.of(400, "Header '%s' has invalid value: %s".formatted(name, strippedHeader));
                    }

                    try {
                        result.add(Double.parseDouble(s));
                    } catch (NumberFormatException e) {
                        throw HttpServerResponseException.of(400, "Header '%s' has invalid value: %s".formatted(name, s));
                    }
                });
            }
        }

        return Collections.unmodifiableSet(result);
    }

    public static UUID parseHeaderUuid(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseHeaderUuidNullable(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        } else {
            return result;
        }
    }

    @Nullable
    public static UUID parseHeaderUuidNullable(HttpServerRequest request, String name) throws HttpServerResponseException {
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
        } catch (IllegalArgumentException e) {
            throw HttpServerResponseException.of(400, "Header '%s' has invalid value: %s".formatted(name, first));
        }
    }

    public static List<UUID> parseHeaderUuidList(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseHeaderUuidListNullable(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static List<UUID> parseHeaderUuidListNullable(HttpServerRequest request, String name) throws HttpServerResponseException {
        var headers = request.headers().getAll(name);
        if (headers == null) {
            return null;
        } else if (headers.isEmpty()) {
            return List.of();
        }

        List<UUID> result = new ArrayList<>(headers.size());
        for (String header : headers) {
            var strippedHeader = header.strip();
            if (!strippedHeader.isEmpty()) {
                forEachCommaSeparatedHeaderValue(strippedHeader, s -> {
                    if (s.isEmpty()) {
                        throw HttpServerResponseException.of(400, "Header '%s' has invalid value: %s".formatted(name, strippedHeader));
                    }

                    try {
                        result.add(UUID.fromString(s));
                    } catch (IllegalArgumentException e) {
                        throw HttpServerResponseException.of(400, "Header '%s' has invalid value: %s".formatted(name, s));
                    }
                });
            }
        }

        return Collections.unmodifiableList(result);
    }

    public static Set<UUID> parseHeaderUuidSet(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseHeaderUuidSetNullable(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static Set<UUID> parseHeaderUuidSetNullable(HttpServerRequest request, String name) throws HttpServerResponseException {
        var headers = request.headers().getAll(name);
        if (headers == null) {
            return null;
        } else if (headers.isEmpty()) {
            return Set.of();
        }

        Set<UUID> result = new LinkedHashSet<>(calculateHashSetCapacity(headers.size()));
        for (String header : headers) {
            var strippedHeader = header.strip();
            if (!strippedHeader.isEmpty()) {
                forEachCommaSeparatedHeaderValue(strippedHeader, s -> {
                    if (s.isEmpty()) {
                        throw HttpServerResponseException.of(400, "Header '%s' has invalid value: %s".formatted(name, strippedHeader));
                    }

                    try {
                        result.add(UUID.fromString(s));
                    } catch (IllegalArgumentException e) {
                        throw HttpServerResponseException.of(400, "Header '%s' has invalid value: %s".formatted(name, s));
                    }
                });
            }
        }

        return Collections.unmodifiableSet(result);
    }

    public static <T> List<T> parseHeaderSomeList(HttpServerRequest request, String name, HttpServerParameterReader<T> mapping) throws HttpServerResponseException {
        var result = parseHeaderSomeListNullable(request, name, mapping);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static <T> List<T> parseHeaderSomeListNullable(HttpServerRequest request, String name, HttpServerParameterReader<T> mapping) throws HttpServerResponseException {
        var headers = request.headers().getAll(name);
        if (headers == null) {
            return null;
        } else if (headers.isEmpty()) {
            return List.of();
        }

        var result = new ArrayList<T>(headers.size());
        for (String header : headers) {
            var strippedHeader = header.strip();
            if (!strippedHeader.isEmpty()) {
                forEachCommaSeparatedRawHeaderValue(strippedHeader, s -> {
                    try {
                        T value = mapping.read(s);
                        result.add(value);
                    } catch (HttpServerResponseException e) {
                        throw e;
                    } catch (Exception e) {
                        throw HttpServerResponseException.of(400, "Header '%s' has invalid value: %s due to: ".formatted(name, s) + e.getMessage());
                    }
                });
            }
        }

        return Collections.unmodifiableList(result);
    }

    public static <T> Set<T> parseHeaderSomeSet(HttpServerRequest request, String name, HttpServerParameterReader<T> mapping) throws HttpServerResponseException {
        var result = parseHeaderSomeSetNullable(request, name, mapping);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static <T> Set<T> parseHeaderSomeSetNullable(HttpServerRequest request, String name, HttpServerParameterReader<T> mapping) throws HttpServerResponseException {
        var headers = request.headers().getAll(name);
        if (headers == null) {
            return null;
        } else if (headers.isEmpty()) {
            return Set.of();
        }

        var result = new LinkedHashSet<T>(calculateHashSetCapacity(headers.size()));
        for (String header : headers) {
            var strippedHeader = header.strip();
            if (!strippedHeader.isEmpty()) {
                forEachCommaSeparatedRawHeaderValue(strippedHeader, s -> {
                    try {
                        T value = mapping.read(s);
                        result.add(value);
                    } catch (HttpServerResponseException e) {
                        throw e;
                    } catch (Exception e) {
                        throw HttpServerResponseException.of(400, "Header '%s' has invalid value: %s due to: ".formatted(name, s) + e.getMessage());
                    }
                });
            }
        }

        return Collections.unmodifiableSet(result);
    }

    /*
     * Query: String, Integer, Long, Double, Boolean, UUID
     */
    public static UUID parseQueryUuid(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseQueryUuidNullable(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }

        return result;
    }

    @Nullable
    public static UUID parseQueryUuidNullable(HttpServerRequest request, String name) throws HttpServerResponseException {
        var param = request.queryParams().get(name);
        if (param == null || param.isEmpty()) {
            return null;
        }

        var first = param.iterator().next().strip();
        if (first.isEmpty()) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
        }

        try {
            return UUID.fromString(first);
        } catch (IllegalArgumentException e) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid value: %s".formatted(name, param));
        }
    }

    public static String parseQueryString(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseQueryStringNullable(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }

        return result;
    }

    @Nullable
    public static String parseQueryStringNullable(HttpServerRequest request, String name) throws HttpServerResponseException {
        var param = request.queryParams().get(name);
        if (param == null || param.isEmpty()) {
            return null;
        }

        return param.iterator().next();
    }

    public static int parseQueryInteger(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseQueryIntegerNullable(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static Integer parseQueryIntegerNullable(HttpServerRequest request, String name) throws HttpServerResponseException {
        var param = request.queryParams().get(name);
        if (param == null || param.isEmpty()) {
            return null;
        }

        var first = param.iterator().next().strip();
        if (first.isEmpty()) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
        }

        try {
            return Integer.parseInt(first);
        } catch (NumberFormatException e) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid value: %s".formatted(name, first));
        }
    }

    public static long parseQueryLong(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseQueryLongNullable(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static Long parseQueryLongNullable(HttpServerRequest request, String name) throws HttpServerResponseException {
        var param = request.queryParams().get(name);
        if (param == null || param.isEmpty()) {
            return null;
        }

        var first = param.iterator().next().strip();
        if (first.isEmpty()) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
        }

        try {
            return Long.parseLong(first);
        } catch (NumberFormatException e) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid value: %s".formatted(name, first));
        }
    }

    public static boolean parseQueryBoolean(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseQueryBooleanNullable(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static Boolean parseQueryBooleanNullable(HttpServerRequest request, String name) throws HttpServerResponseException {
        var param = request.queryParams().get(name);
        if (param == null || param.isEmpty()) {
            return null;
        }

        var first = param.iterator().next().strip();
        if (first.isEmpty()) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
        }

        if ("true".equalsIgnoreCase(first)) {
            return true;
        } else if ("false".equalsIgnoreCase(first)) {
            return false;
        } else {
            throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid value: %s".formatted(name, param));
        }
    }

    public static double parseQueryDouble(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseQueryDoubleNullable(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static Double parseQueryDoubleNullable(HttpServerRequest request, String name) throws HttpServerResponseException {
        var param = request.queryParams().get(name);
        if (param == null || param.isEmpty()) {
            return null;
        }

        var first = param.iterator().next().strip();
        if (first.isEmpty()) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
        }

        try {
            return Double.parseDouble(first);
        } catch (NumberFormatException e) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid value: %s".formatted(name, first));
        }
    }

    /*
     * Query: List<String>, List<Integer>, List<Long>, List<Double>, List<Boolean>, List<UUID>
     */
    public static List<Integer> parseQueryIntegerList(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseQueryIntegerListNullable(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static List<Integer> parseQueryIntegerListNullable(HttpServerRequest request, String name) throws HttpServerResponseException {
        var params = request.queryParams().get(name);
        if (params == null) {
            return null;
        } else if (params.isEmpty()) {
            return List.of();
        }

        var result = new ArrayList<Integer>(params.size());
        for (var param : params) {
            if (param != null) {
                param = param.strip();
                if (param.isEmpty()) {
                    throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
                }
                try {
                    result.add(Integer.parseInt(param));
                } catch (NumberFormatException e) {
                    throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid value: %s".formatted(name, param));
                }
            }
        }
        return result;
    }

    public static List<UUID> parseQueryUuidList(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseQueryUuidListNullable(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static List<UUID> parseQueryUuidListNullable(HttpServerRequest request, String name) throws HttpServerResponseException {
        var params = request.queryParams().get(name);
        if (params == null) {
            return null;
        } else if (params.isEmpty()) {
            return List.of();
        }

        var result = new ArrayList<UUID>(params.size());
        for (var param : params) {
            if (param != null) {
                param = param.strip();
                if (param.isEmpty()) {
                    throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
                }
                try {
                    result.add(UUID.fromString(param));
                } catch (IllegalArgumentException e) {
                    throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid value: %s".formatted(name, param));
                }
            }
        }
        return result;
    }

    public static List<String> parseQueryStringList(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseQueryStringListNullable(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static List<String> parseQueryStringListNullable(HttpServerRequest request, String name) throws HttpServerResponseException {
        var params = request.queryParams().get(name);
        if (params == null) {
            return null;
        } else if (params.isEmpty()) {
            return List.of();
        }

        return Collections.unmodifiableList(params);
    }

    public static List<Long> parseQueryLongList(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseQueryLongListNullable(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static List<Long> parseQueryLongListNullable(HttpServerRequest request, String name) throws HttpServerResponseException {
        var params = request.queryParams().get(name);
        if (params == null) {
            return null;
        } else if (params.isEmpty()) {
            return List.of();
        }

        var result = new ArrayList<Long>(params.size());
        for (var param : params) {
            if (param != null) {
                param = param.strip();
                if (param.isEmpty()) {
                    throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
                }
                try {
                    result.add(Long.parseLong(param));
                } catch (NumberFormatException e) {
                    throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid value: %s".formatted(name, param));
                }
            }
        }
        return result;
    }

    public static List<Double> parseQueryDoubleList(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseQueryDoubleListNullable(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static List<Double> parseQueryDoubleListNullable(HttpServerRequest request, String name) throws HttpServerResponseException {
        var params = request.queryParams().get(name);
        if (params == null) {
            return null;
        } else if (params.isEmpty()) {
            return List.of();
        }

        var result = new ArrayList<Double>(params.size());
        for (var param : params) {
            if (param != null) {
                param = param.strip();
                if (param.isEmpty()) {
                    throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
                }
                try {
                    result.add(Double.parseDouble(param));
                } catch (NumberFormatException e) {
                    throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid value: %s".formatted(name, param));
                }
            }
        }
        return result;
    }

    public static List<Boolean> parseQueryBooleanList(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseQueryBooleanListNullable(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static List<Boolean> parseQueryBooleanListNullable(HttpServerRequest request, String name) throws HttpServerResponseException {
        var params = request.queryParams().get(name);
        if (params == null) {
            return null;
        } else if (params.isEmpty()) {
            return List.of();
        }

        var result = new ArrayList<Boolean>(params.size());
        for (var param : params) {
            if (param != null) {
                param = param.strip();
                if (param.isEmpty()) {
                    throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
                }
                if ("true".equalsIgnoreCase(param)) {
                    result.add(true);
                } else if ("false".equalsIgnoreCase(param)) {
                    result.add(false);
                } else {
                    throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid value: %s".formatted(name, param));
                }
            }
        }
        return result;
    }

    public static Set<Integer> parseQueryIntegerSet(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseQueryIntegerSetNullable(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static Set<Integer> parseQueryIntegerSetNullable(HttpServerRequest request, String name) throws HttpServerResponseException {
        var params = request.queryParams().get(name);
        if (params == null) {
            return null;
        } else if (params.isEmpty()) {
            return Set.of();
        }

        var result = new LinkedHashSet<Integer>(calculateHashSetCapacity(params.size()));
        for (var param : params) {
            if (param != null) {
                param = param.strip();
                if (param.isEmpty()) {
                    throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
                }
                try {
                    result.add(Integer.parseInt(param));
                } catch (NumberFormatException e) {
                    throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid value: %s".formatted(name, param));
                }
            }
        }
        return Collections.unmodifiableSet(result);
    }

    public static Set<UUID> parseQueryUuidSet(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseQueryUuidSetNullable(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static Set<UUID> parseQueryUuidSetNullable(HttpServerRequest request, String name) throws HttpServerResponseException {
        var params = request.queryParams().get(name);
        if (params == null) {
            return null;
        } else if (params.isEmpty()) {
            return Set.of();
        }

        var result = new LinkedHashSet<UUID>(calculateHashSetCapacity(params.size()));
        for (var param : params) {
            if (param != null) {
                param = param.strip();
                if (param.isEmpty()) {
                    throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
                }
                try {
                    result.add(UUID.fromString(param));
                } catch (IllegalArgumentException e) {
                    throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid value: %s".formatted(name, param));
                }
            }
        }
        return Collections.unmodifiableSet(result);
    }

    public static Set<String> parseQueryStringSet(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseQueryStringSetNullable(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static Set<String> parseQueryStringSetNullable(HttpServerRequest request, String name) throws HttpServerResponseException {
        var params = request.queryParams().get(name);
        if (params == null) {
            return null;
        } else if (params.isEmpty()) {
            return Set.of();
        }

        var result = new LinkedHashSet<String>(calculateHashSetCapacity(params.size()));
        for (var str : params) {
            if (str != null) {
                result.add(str);
            }
        }

        return Collections.unmodifiableSet(result);
    }

    public static Set<Long> parseQueryLongSet(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseQueryLongSetNullable(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static Set<Long> parseQueryLongSetNullable(HttpServerRequest request, String name) throws HttpServerResponseException {
        var params = request.queryParams().get(name);
        if (params == null) {
            return null;
        } else if (params.isEmpty()) {
            return Set.of();
        }

        var result = new LinkedHashSet<Long>(calculateHashSetCapacity(params.size()));
        for (var param : params) {
            if (param != null) {
                param = param.strip();
                if (param.isEmpty()) {
                    throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
                }
                try {
                    result.add(Long.parseLong(param));
                } catch (NumberFormatException e) {
                    throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid value: %s".formatted(name, param));
                }
            }
        }
        return Collections.unmodifiableSet(result);
    }

    public static Set<Double> parseQueryDoubleSet(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseQueryDoubleSetNullable(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static Set<Double> parseQueryDoubleSetNullable(HttpServerRequest request, String name) throws HttpServerResponseException {
        var params = request.queryParams().get(name);
        if (params == null) {
            return null;
        } else if (params.isEmpty()) {
            return Set.of();
        }

        var result = new LinkedHashSet<Double>(calculateHashSetCapacity(params.size()));
        for (var param : params) {
            if (param != null) {
                param = param.strip();
                if (param.isEmpty()) {
                    throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
                }
                try {
                    result.add(Double.parseDouble(param));
                } catch (NumberFormatException e) {
                    throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid value: %s".formatted(name, param));
                }
            }
        }
        return Collections.unmodifiableSet(result);
    }

    public static Set<Boolean> parseQueryBooleanSet(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = parseQueryBooleanSetNullable(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static Set<Boolean> parseQueryBooleanSetNullable(HttpServerRequest request, String name) throws HttpServerResponseException {
        var params = request.queryParams().get(name);
        if (params == null) {
            return null;
        } else if (params.isEmpty()) {
            return Set.of();
        }

        var result = new LinkedHashSet<Boolean>(calculateHashSetCapacity(params.size()));
        for (var param : params) {
            if (param != null) {
                param = param.strip();
                if (param.isEmpty()) {
                    throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
                }

                if ("true".equalsIgnoreCase(param)) {
                    result.add(true);
                } else if ("false".equalsIgnoreCase(param)) {
                    result.add(false);
                } else {
                    throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid value: %s".formatted(name, param));
                }
            }
        }
        return Collections.unmodifiableSet(result);
    }

    public static <T> List<T> parseQuerySomeList(HttpServerRequest request, String name, HttpServerParameterReader<T> mapping) throws HttpServerResponseException {
        var result = parseQuerySomeListNullable(request, name, mapping);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static <T> List<T> parseQuerySomeListNullable(HttpServerRequest request, String name, HttpServerParameterReader<T> mapping) throws HttpServerResponseException {
        var params = request.queryParams().get(name);
        if (params == null) {
            return null;
        } else if (params.isEmpty()) {
            return List.of();
        }

        var result = new ArrayList<T>(params.size());
        for (var param : params) {
            if (param != null) {
                try {
                    T value = mapping.read(param);
                    result.add(value);
                } catch (HttpServerResponseException e) {
                    throw e;
                } catch (Exception e) {
                    throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid value: %s".formatted(name, param));
                }
            }
        }

        return Collections.unmodifiableList(result);
    }

    public static <T> Set<T> parseQuerySomeSet(HttpServerRequest request, String name, HttpServerParameterReader<T> mapping) throws HttpServerResponseException {
        var result = parseQuerySomeSetNullable(request, name, mapping);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static <T> Set<T> parseQuerySomeSetNullable(HttpServerRequest request, String name, HttpServerParameterReader<T> mapping) throws HttpServerResponseException {
        var params = request.queryParams().get(name);
        if (params == null) {
            return null;
        } else if (params.isEmpty()) {
            return Set.of();
        }

        var result = new LinkedHashSet<T>(calculateHashSetCapacity(params.size()));
        for (var param : params) {
            if (param != null) {
                try {
                    T value = mapping.read(param);
                    result.add(value);
                } catch (HttpServerResponseException e) {
                    throw e;
                } catch (Exception e) {
                    throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid value: %s".formatted(name, param));
                }
            }
        }

        return Collections.unmodifiableSet(result);
    }

    // cookies
    @Nullable
    public static Cookie parseCookieNullable(HttpServerRequest request, String name) {
        var cookies = request.cookies();
        for (var cookie : cookies) {
            if (Objects.equals(cookie.name(), name)) {
                return cookie;
            }
        }
        return null;
    }

    public static Cookie parseCookie(HttpServerRequest request, String name) {
        var result = parseCookieNullable(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Cookie '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static String parseCookieStringNullable(HttpServerRequest request, String name) {
        var cookie = parseCookieNullable(request, name);
        if (cookie != null) {
            return cookie.value();
        }
        return null;
    }

    public static String parseCookieString(HttpServerRequest request, String name) {
        var result = parseCookieStringNullable(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Cookie '%s' is required".formatted(name));
        }
        return result;
    }
}
