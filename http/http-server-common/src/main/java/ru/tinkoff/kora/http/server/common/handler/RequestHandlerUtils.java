package ru.tinkoff.kora.http.server.common.handler;

import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponseException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ParametersAreNonnullByDefault
public class RequestHandlerUtils {
    private RequestHandlerUtils() {
    }

    /*
     * Path: String, UUID, Integer, Long, Double, Enum<?>
     */

    @Nonnull
    public static String parseStringPathParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.pathParams().get(name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Path parameter '%s' is required".formatted(name));
        }
        return result;
    }

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

    @Nonnull
    public static <T extends Enum<T>> T parseEnumPathParameter(HttpServerRequest request, Class<T> enumType, String name) throws HttpServerResponseException {
        var result = request.pathParams().get(name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Path parameter '%s' is required".formatted(name));
        }
        try {
            return Enum.valueOf(enumType, result);
        } catch (Exception exception) {
            throw HttpServerResponseException.of(400, "Path parameter %s(%s) has invalid value".formatted(name, result));
        }
    }

    /*
     * Headers: String, Integer, List<String>, List<Integer>
     */

    @Nonnull
    public static String parseStringHeaderParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.headers().get(name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        }
        return String.join(", ", result);
    }

    @Nullable
    public static String parseOptionalStringHeaderParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.headers().get(name);
        if (result == null || result.isEmpty()) {
            return null;
        }
        return String.join(", ", result);
    }

    public static List<String> parseStringListHeaderParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.headers().get(name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        }
        return result.stream()
            .flatMap(h -> Stream.of(h.split(",")))
            .map(String::trim)
            .filter(Predicate.not(String::isBlank))
            .collect(Collectors.toList());
    }

    public static int parseIntegerHeaderParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.headers().get(name);
        if (result == null || result.isEmpty()) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        }
        var first = result.iterator().next().trim();
        try {
            return Integer.parseInt(first);
        } catch (NumberFormatException e) {
            throw HttpServerResponseException.of(400, "Header %s(%s) has invalid value".formatted(name, first));
        }
    }

    @Nullable
    public static Integer parseOptionalIntegerHeaderParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.headers().get(name);
        if (result == null || result.isEmpty()) {
            return null;
        }
        var first = result.iterator().next().trim();
        try {
            return Integer.parseInt(first);
        } catch (NumberFormatException e) {
            throw HttpServerResponseException.of(400, "Header %s(%s) has invalid value".formatted(name, first));
        }
    }

    public static List<Integer> parseIntegerListHeaderParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var headers = request.headers().get(name);
        if (headers == null || headers.isEmpty()) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        }
        List<Integer> result = new ArrayList<>();
        for (String header: headers) {
            header = header.trim();
            if (!header.isEmpty()) {
                String[] split = header.split(",");
                for (String s: split) {
                    s = s.trim();
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

        return result;
    }


    /*
        Query: String, Integer, Long, Double, Boolean, Enum<?>, List<String>, List<Integer>, List<Long>, List<Double>, List<Boolean>, List<Enum<?>>, UUID
     */

    @Nonnull
    public static String parseStringQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.queryParams().get(name);
        if (result == null || result.isEmpty()) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }

        return result.iterator().next();
    }

    @Nullable
    public static String parseOptionalStringQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.queryParams().get(name);
        if (result == null || result.isEmpty()) {
            return null;
        }
        return result.iterator().next();
    }

    public static List<String> parseStringListQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.queryParams().get(name);
        if (result == null) {
            return List.of();
        }
        return List.copyOf(result);
    }


    public static int parseIntegerQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.queryParams().get(name);
        if (result == null || result.isEmpty()) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        var first = result.iterator().next();
        try {
            return Integer.parseInt(first);
        } catch (NumberFormatException e) {
            throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".formatted(name, first));
        }
    }

    @Nullable
    public static Integer parseOptionalIntegerQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.queryParams().get(name);
        if (result == null || result.isEmpty()) {
            return null;
        }
        var first = result.iterator().next();
        try {
            return Integer.parseInt(first);
        } catch (NumberFormatException e) {
            throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".formatted(name, first));
        }
    }

    public static List<Integer> parseIntegerListQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.queryParams().get(name);
        if (result == null) {
            return List.of();
        }
        return result.stream()
            .map(v -> {
                if (v == null || v.isBlank()) {
                    return null;
                }
                try {
                    return Integer.parseInt(v);
                } catch (NumberFormatException e) {
                    throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".formatted(name, v));
                }
            })
            .collect(Collectors.toList());
    }


    public static long parseLongQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.queryParams().get(name);
        if (result == null || result.isEmpty()) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        var first = result.iterator().next();
        try {
            return Long.parseLong(first);
        } catch (NumberFormatException e) {
            throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".formatted(name, first));
        }
    }

    @Nullable
    public static Long parseOptionalLongQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.queryParams().get(name);
        if (result == null || result.isEmpty()) {
            return null;
        }
        var first = result.iterator().next();
        try {
            return Long.parseLong(first);
        } catch (NumberFormatException e) {
            throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".formatted(name, first));
        }
    }

    public static List<Long> parseLongListQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.queryParams().get(name);
        if (result == null) {
            return List.of();
        }
        return result.stream()
            .map(v -> {
                if (v == null || v.isBlank()) {
                    return null;
                }
                try {
                    return Long.parseLong(v);
                } catch (NumberFormatException e) {
                    throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".formatted(name, v));
                }
            })
            .collect(Collectors.toList());
    }


    public static double parseDoubleQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.queryParams().get(name);
        if (result == null || result.isEmpty()) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        var first = result.iterator().next();
        try {
            return Double.parseDouble(first);
        } catch (NumberFormatException e) {
            throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".formatted(name, first));
        }
    }

    @Nullable
    public static Double parseOptionalDoubleQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.queryParams().get(name);
        if (result == null || result.isEmpty()) {
            return null;
        }
        var first = result.iterator().next();
        try {
            return Double.parseDouble(first);
        } catch (NumberFormatException e) {
            throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".formatted(name, first));
        }
    }

    public static List<Double> parseDoubleListQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.queryParams().get(name);
        if (result == null) {
            return List.of();
        }
        return result.stream()
            .map(v -> {
                if (v == null || v.isBlank()) {
                    return null;
                }
                try {
                    return Double.parseDouble(v);
                } catch (NumberFormatException e) {
                    throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".formatted(name, v));
                }
            })
            .collect(Collectors.toList());
    }


    public static Boolean parseBooleanQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.queryParams().get(name);
        if (result == null || result.isEmpty()) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        var first = result.iterator().next();
        try {
            // todo
            return Boolean.parseBoolean(first);
        } catch (NumberFormatException e) {
            throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".formatted(name, first));
        }
    }

    @Nullable
    public static Boolean parseOptionalBooleanQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.queryParams().get(name);
        if (result == null || result.isEmpty()) {
            return null;
        }
        var first = result.iterator().next();
        try {
            // todo
            return Boolean.parseBoolean(first);
        } catch (NumberFormatException e) {
            throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".formatted(name, first));
        }
    }

    public static List<Boolean> parseBooleanListQueryParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.queryParams().get(name);
        if (result == null) {
            return List.of();
        }
        return result.stream()
            .map(v -> {
                // todo
                if (v == null || v.isBlank()) {
                    return null;
                }
                try {
                    return Boolean.parseBoolean(v);
                } catch (NumberFormatException e) {
                    throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".formatted(name, v));
                }
            })
            .collect(Collectors.toList());
    }


    public static <T extends Enum<T>> T parseEnumQueryParameter(HttpServerRequest request, Class<T> type, String name) throws HttpServerResponseException {
        var result = parseOptionalEnumQueryParameter(request, type, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static <T extends Enum<T>> T parseOptionalEnumQueryParameter(HttpServerRequest request, Class<T> type, String name) throws HttpServerResponseException {
        var result = request.queryParams().get(name);
        if (result == null || result.isEmpty()) {
            return null;
        }
        var first = result.iterator().next();
        try {
            return Enum.valueOf(type, first);
        } catch (Exception exception) {
            throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".formatted(name, result));
        }
    }

    public static <T extends Enum<T>> List<T> parseEnumListQueryParameter(HttpServerRequest request, Class<T> type, String name) throws HttpServerResponseException {
        var result = request.queryParams().get(name);
        if (result == null) {
            return List.of();
        }
        return result.stream()
            .map(v -> {
                if (v == null || v.isBlank()) {
                    return null;
                }
                try {
                    return Enum.valueOf(type, v);
                } catch (Exception exception) {
                    throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".formatted(name, result));
                }
            })
            .collect(Collectors.toList());
    }

}
