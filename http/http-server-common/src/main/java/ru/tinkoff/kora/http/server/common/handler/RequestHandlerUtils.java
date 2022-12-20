package ru.tinkoff.kora.http.server.common.handler;

import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponseException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ParametersAreNonnullByDefault
public final class RequestHandlerUtils {

    private RequestHandlerUtils() {}

    /*
     * Path: String, UUID, Integer, Long, Double, Enum
     */
    @Nonnull
    public static String parseStringPathParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.pathParams().get(name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Path parameter '%s' is required".formatted(name));
        }
        return result;
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
        var result = parseOptionalStringListHeaderParameter(request, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        }

        return result;
    }

    @Nullable
    public static List<String> parseOptionalStringListHeaderParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.headers().get(name);
        if (result == null) {
            return null;
        }

        return result.stream()
            .flatMap(h -> Stream.of(h.split(",")))
            .map(String::trim)
            .filter(Predicate.not(String::isBlank))
            .toList();
    }

    public static int parseIntegerHeaderParameter(HttpServerRequest request, String name) throws HttpServerResponseException {
        var result = request.headers().get(name);
        if (result == null || result.isEmpty()) {
            throw HttpServerResponseException.of(400, "Header '%s' is required".formatted(name));
        }

        var first = result.iterator().next().trim();
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
        var result = request.headers().get(name);
        if (result == null || result.isEmpty()) {
            return null;
        }

        var first = result.iterator().next().trim();
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
        var headers = request.headers().get(name);
        if (headers == null || headers.isEmpty()) {
            return null;
        }

        List<Integer> result = new ArrayList<>();
        for (String header : headers) {
            header = header.trim();
            if (!header.isEmpty()) {
                String[] split = header.split(",");
                for (String s : split) {
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

        return List.copyOf(result);
    }

    /*
     * Query: String, Integer, Long, Double, Boolean, Enum, UUID
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

        var first = result.iterator().next().trim();
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

        var first = result.iterator().next().trim();
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

        var first = result.iterator().next().trim();
        if (first.isEmpty()) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
        }

        try {
            return Long.parseLong(first);
        } catch (NumberFormatException e) {
            throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".formatted(name, first));
        }
    }

    @Nonnull
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

        var first = result.iterator().next().trim();
        if (first.isEmpty()) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
        }

        try {
            return Enum.valueOf(type, first);
        } catch (Exception exception) {
            throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".formatted(name, result));
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

        var first = result.iterator().next().trim();
        if (first.isEmpty()) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
        }

        try {
            // todo
            return Boolean.parseBoolean(first);
        } catch (NumberFormatException e) {
            throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".formatted(name, first));
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

        var first = result.iterator().next().trim();
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
     * Query: List<String>, List<Integer>, List<Long>, List<Double>, List<Boolean>, List<UUID>, List<Enum>
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

        return result.stream()
            .filter(Objects::nonNull)
            .map(v -> {
                v = v.trim();
                if (v.isEmpty()) {
                    throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
                }

                try {
                    return Integer.parseInt(v);
                } catch (NumberFormatException e) {
                    throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".formatted(name, v));
                }
            })
            .toList();
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

        return result.stream()
            .filter(Objects::nonNull)
            .map(v -> {
                v = v.trim();
                if (v.isEmpty()) {
                    throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
                }

                try {
                    return UUID.fromString(v);
                } catch (IllegalArgumentException e) {
                    throw HttpServerResponseException.of(400, "Path parameter '%s' has invalid value '%s'".formatted(name, result));
                }
            })
            .toList();
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

        return result.stream()
            .filter(Objects::nonNull)
            .map(v -> {
                v = v.trim();
                if (v.isEmpty()) {
                    throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
                }

                try {
                    return Long.parseLong(v);
                } catch (NumberFormatException e) {
                    throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".formatted(name, v));
                }
            })
            .toList();
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

        return result.stream()
            .filter(Objects::nonNull)
            .map(v -> {
                v = v.trim();
                if (v.isEmpty()) {
                    throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
                }

                try {
                    return Double.parseDouble(v);
                } catch (NumberFormatException e) {
                    throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".formatted(name, v));
                }
            })
            .toList();
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

        return result.stream()
            .filter(Objects::nonNull)
            .map(v -> {
                v = v.trim();
                if (v.isEmpty()) {
                    throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
                }

                try {
                    return Boolean.parseBoolean(v);
                } catch (NumberFormatException e) {
                    throw HttpServerResponseException.of(400, "Query parameter %s(%s) has invalid value".formatted(name, v));
                }
            })
            .toList();
    }

    @Nonnull
    public static <T extends Enum<T>> List<T> parseEnumListQueryParameter(HttpServerRequest request, Class<T> type, String name) throws HttpServerResponseException {
        var result = parseOptionalEnumListQueryParameter(request, type, name);
        if (result == null) {
            throw HttpServerResponseException.of(400, "Query parameter '%s' is required".formatted(name));
        }
        return result;
    }

    @Nullable
    public static <T extends Enum<T>> List<T> parseOptionalEnumListQueryParameter(HttpServerRequest request, Class<T> type, String name) throws HttpServerResponseException {
        var result = request.queryParams().get(name);
        if (result == null) {
            return null;
        }

        return result.stream()
            .filter(Objects::nonNull)
            .map(v -> {
                v = v.trim();
                if (v.isEmpty()) {
                    throw HttpServerResponseException.of(400, "Query parameter '%s' has invalid blank string value".formatted(name));
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
