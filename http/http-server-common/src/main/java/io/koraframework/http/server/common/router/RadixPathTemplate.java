package io.koraframework.http.server.common.router;

import org.jspecify.annotations.Nullable;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Parsed path template optimized for {@link RadixPathTemplateMatcher}.
 *
 * <h2>Representation</h2>
 * <ol>
 *   <li>Parse and normalize the template with the same route grammar as the original
 *       implementation.</li>
 *   <li>Store suffix parts in a compact {@code Part[]} rather than a list wrapper plus backing
 *       array.</li>
 *   <li>Retain capture names in encounter order and precompute parameter count and semantic hash
 *       code.</li>
 * </ol>
 *
 * <h2>Matching algorithm</h2>
 * <p>The radix matcher verifies the static base before calling this class, so hot-path methods
 * receive {@code baseMatched=true} and begin directly at the dynamic suffix. Literal segments are
 * checked with region comparisons. Dedicated one- and two-parameter methods keep capture positions
 * in primitive integers and allocate substrings and result maps only after the full route succeeds.
 * Two distinct parameter names use a specialized fixed-size map rather than a
 * {@link java.util.LinkedHashMap}. Templates with more parameters use the generic single-pass
 * matcher and a reusable caller-supplied map.</p>
 *
 * <h2>Wildcard grammar</h2>
 * <p>A template accepts at most one {@code *}, only as its final character. The wildcard may
 * occupy the complete last segment or follow a literal prefix in that segment. The prefix is
 * matched literally, then {@code *} captures every remaining character, including slashes and an
 * empty remainder. Other placements are rejected while parsing so trie and template matching
 * cannot disagree.</p>
 *
 * <h2>Ordering</h2>
 * <p>Natural ordering is semantic route priority and equivalence, independent of parameter names:
 * exact and literal-heavy routes precede parameterized or wildcard alternatives.</p>
 */
final class RadixPathTemplate implements Comparable<RadixPathTemplate> {

    private static final Part[] EMPTY_PARTS = new Part[0];

    private final String templateString;
    private final boolean template;
    private final String base;
    private final Part[] parts;
    private final Set<String> parameterNames;
    private final boolean trailingSlash;
    private final @Nullable String wildcardPrefix;
    private final int parameterCount;
    private final String[] captureNames;
    private final int hashCode;

    private RadixPathTemplate(String templateString,
                              boolean template,
                              String base,
                              Part[] parts,
                              Set<String> parameterNames,
                              boolean trailingSlash,
                              @Nullable String wildcardPrefix,
                              int parameterCount,
                              String[] captureNames) {
        this.templateString = templateString;
        this.template = template;
        this.base = base;
        this.parts = parts;
        this.parameterNames = parameterNames;
        this.trailingSlash = trailingSlash;
        this.wildcardPrefix = wildcardPrefix;
        this.parameterCount = parameterCount;
        this.captureNames = captureNames;
        this.hashCode = this.calculateHashCode();
    }

    @SuppressWarnings("fallthrough")
    static RadixPathTemplate create(final String inputPath) {
        if (inputPath == null) {
            throw new IllegalArgumentException("Path must be specified");
        }

        final String path;
        if (inputPath.isEmpty()) {
            path = "/";
        } else if (inputPath.charAt(0) != '/') {
            path = "/" + inputPath;
        } else {
            path = inputPath;
        }
        validateWildcard(path);

        int state = 0;
        String base = "";
        ArrayList<Part> parts = null;
        int stringStart = 0;
        int parameterCount = 0;

        for (int i = 0; i < path.length(); i++) {
            final char c = path.charAt(i);
            switch (state) {
                case 0 -> {
                    if (c == '/') {
                        state = 1;
                    } else if (c == '*') {
                        base = path.substring(0, i + 1);
                        stringStart = i;
                        state = 5;
                    }
                }
                case 1 -> {
                    if (c == '{') {
                        base = path.substring(0, i);
                        stringStart = i + 1;
                        state = 2;
                    } else if (c == '*') {
                        base = path.substring(0, i + 1);
                        stringStart = i;
                        state = 5;
                    } else if (c != '/') {
                        state = 0;
                    }
                }
                case 2 -> {
                    if (c == '}') {
                        if (parts == null) {
                            parts = new ArrayList<>(4);
                        }
                        parts.add(Part.parameter(path.substring(stringStart, i)));
                        parameterCount++;
                        stringStart = i;
                        state = 3;
                    }
                }
                case 3 -> {
                    if (c == '/') {
                        state = 4;
                    } else {
                        throw parseException(path);
                    }
                }
                case 4 -> {
                    if (c == '{') {
                        stringStart = i + 1;
                        state = 2;
                    } else if (c != '/') {
                        stringStart = i;
                        state = 5;
                    }
                }
                case 5 -> {
                    if (c == '/') {
                        if (parts == null) {
                            parts = new ArrayList<>(4);
                        }
                        var part = Part.literal(path.substring(stringStart, i));
                        parts.add(part);
                        if (part.wildcard) {
                            parameterCount++;
                        }
                        stringStart = i + 1;
                        state = 4;
                    }
                }
                default -> throw new IllegalStateException();
            }
        }

        boolean trailingSlash = false;
        switch (state) {
            case 1:
                trailingSlash = true;
                // fall through
            case 0:
                base = path;
                break;
            case 2:
                throw parseException(path);
            case 3:
                break;
            case 4:
                trailingSlash = true;
                break;
            case 5:
                if (parts == null) {
                    parts = new ArrayList<>(1);
                }
                var part = Part.literal(path.substring(stringStart));
                parts.add(part);
                if (part.wildcard) {
                    parameterCount++;
                }
                break;
            default:
                throw new IllegalStateException();
        }

        final Part[] partArray = parts == null ? EMPTY_PARTS : parts.toArray(Part[]::new);
        final Set<String> parameterNames;
        final String[] captureNames;
        if (parameterCount == 0) {
            parameterNames = Set.of();
            captureNames = new String[0];
        } else {
            var names = new HashSet<String>(parameterCount);
            captureNames = new String[parameterCount];
            int captureIndex = 0;
            for (var part : partArray) {
                if (part.parameter) {
                    names.add(part.value);
                    captureNames[captureIndex++] = part.value;
                } else if (part.wildcard) {
                    captureNames[captureIndex++] = "*";
                }
            }
            parameterNames = names;
        }

        final int wildcardIndex = base.indexOf('*');
        final String wildcardPrefix;
        final boolean templatePath;
        if (wildcardIndex < 0) {
            wildcardPrefix = null;
            templatePath = state > 1;
        } else {
            wildcardPrefix = base.substring(0, wildcardIndex);
            templatePath = false;
        }
        return new RadixPathTemplate(path, templatePath, base, partArray, parameterNames, trailingSlash,
            wildcardPrefix, parameterCount, captureNames);
    }

    private static void validateWildcard(String path) {
        int wildcard = path.indexOf('*');
        if (wildcard >= 0 && wildcard != path.length() - 1) {
            throw new IllegalArgumentException(
                "Wildcard '*' is only allowed once and as the final character: " + path
            );
        }
    }

    private static IllegalArgumentException parseException(String path) {
        return new IllegalArgumentException("Could not parse URI template %s, exception at char %s".formatted(path, path.length()));
    }

    @Nullable
    Map<String, String> matchSingleParameter(final String path, boolean baseMatched) {
        var wildcardPrefix = this.wildcardPrefix;
        if (wildcardPrefix != null) {
            if (!baseMatched && !path.startsWith(wildcardPrefix)) {
                return null;
            }
            return Map.of("*", path.substring(wildcardPrefix.length()));
        }

        if (!baseMatched && !path.startsWith(this.base)) {
            return null;
        }
        if (this.trailingSlash && path.charAt(path.length() - 1) != '/') {
            return null;
        }

        int currentPartPosition = 0;
        Part current = this.parts[0];
        int stringStart = this.base.length();
        int captureStart = -1;
        int captureEnd = -1;
        int i;
        for (i = stringStart; i < path.length(); i++) {
            if (path.charAt(i) != '/') {
                continue;
            }
            if (current.parameter) {
                captureStart = stringStart;
                captureEnd = i;
            } else if (segmentNotEquals(path, stringStart, i, current.value)) {
                return null;
            }
            currentPartPosition++;
            if (currentPartPosition == this.parts.length) {
                if (!this.trailingSlash || i != path.length() - 1 || captureStart < 0) {
                    return null;
                }
                return Map.of(this.captureNames[0], path.substring(captureStart, captureEnd));
            }
            current = this.parts[currentPartPosition];
            stringStart = i + 1;
        }
        if (currentPartPosition + 1 != this.parts.length) {
            return null;
        }

        if (current.parameter) {
            if (stringStart == i) {
                return null;
            }
            captureStart = stringStart;
            captureEnd = i;
        } else if (segmentNotEquals(path, stringStart, i, current.value)) {
            return null;
        }
        return captureStart < 0
            ? null
            : Map.of(this.captureNames[0], path.substring(captureStart, captureEnd));
    }

    @Nullable
    Map<String, String> matchTwoParameters(final String path, boolean baseMatched) {
        if (!baseMatched && !path.startsWith(this.base)) {
            return null;
        }
        if (this.trailingSlash && path.charAt(path.length() - 1) != '/') {
            return null;
        }

        int firstStart = -1;
        int firstEnd = -1;
        int secondStart = -1;
        int secondEnd = -1;
        int captured = 0;
        int currentPartPosition = 0;
        Part current = this.parts[0];
        int stringStart = this.base.length();
        int i;
        for (i = stringStart; i < path.length(); i++) {
            if (current.wildcard) {
                break;
            }
            if (path.charAt(i) != '/') {
                continue;
            }
            if (current.parameter) {
                if (captured++ == 0) {
                    firstStart = stringStart;
                    firstEnd = i;
                } else {
                    secondStart = stringStart;
                    secondEnd = i;
                }
            } else if (segmentNotEquals(path, stringStart, i, current.value)) {
                return null;
            }
            currentPartPosition++;
            if (currentPartPosition == this.parts.length) {
                if (!this.trailingSlash || i != path.length() - 1 || captured != 2) {
                    return null;
                }
                return this.twoParameterMap(path, firstStart, firstEnd, secondStart, secondEnd);
            }
            current = this.parts[currentPartPosition];
            stringStart = i + 1;
        }
        if (currentPartPosition + 1 != this.parts.length) {
            return null;
        }

        if (current.wildcard) {
            int wildcardStart = wildcardStart(path, stringStart, current);
            if (wildcardStart < 0) {
                return null;
            }
            if (captured++ == 0) {
                firstStart = wildcardStart;
                firstEnd = path.length();
            } else {
                secondStart = wildcardStart;
                secondEnd = path.length();
            }
        } else if (current.parameter) {
            if (stringStart == i) {
                return null;
            }
            if (captured++ == 0) {
                firstStart = stringStart;
                firstEnd = i;
            } else {
                secondStart = stringStart;
                secondEnd = i;
            }
        } else if (segmentNotEquals(path, stringStart, i, current.value)) {
            return null;
        }

        return captured == 2
            ? this.twoParameterMap(path, firstStart, firstEnd, secondStart, secondEnd)
            : null;
    }

    private Map<String, String> twoParameterMap(String path,
                                                int firstStart,
                                                int firstEnd,
                                                int secondStart,
                                                int secondEnd) {
        if (this.captureNames[0].equals(this.captureNames[1])) {
            return Map.of(this.captureNames[1], path.substring(secondStart, secondEnd));
        }
        return new TwoParameterMap(
            this.captureNames[0], path.substring(firstStart, firstEnd),
            this.captureNames[1], path.substring(secondStart, secondEnd)
        );
    }

    boolean matches(final String path, final Map<String, String> pathParameters, boolean baseMatched) {
        var wildcardPrefix = this.wildcardPrefix;
        if (wildcardPrefix != null) {
            if (!baseMatched && !path.startsWith(wildcardPrefix)) {
                return false;
            }
            pathParameters.put("*", path.substring(wildcardPrefix.length()));
            return true;
        }

        if (!baseMatched && !path.startsWith(this.base)) {
            return false;
        }
        final int baseLength = this.base.length();
        if (!this.template) {
            return path.length() == baseLength;
        }
        if (this.trailingSlash && path.charAt(path.length() - 1) != '/') {
            return false;
        }

        int currentPartPosition = 0;
        Part current = this.parts[0];
        int stringStart = baseLength;
        int i;
        for (i = baseLength; i < path.length(); i++) {
            if (current.wildcard) {
                break;
            }
            if (path.charAt(i) == '/') {
                if (current.parameter) {
                    pathParameters.put(current.value, path.substring(stringStart, i));
                } else if (segmentNotEquals(path, stringStart, i, current.value)) {
                    pathParameters.clear();
                    return false;
                }
                currentPartPosition++;
                if (currentPartPosition == this.parts.length) {
                    return this.trailingSlash && i == path.length() - 1;
                }
                current = this.parts[currentPartPosition];
                stringStart = i + 1;
            }
        }
        if (currentPartPosition + 1 != this.parts.length) {
            pathParameters.clear();
            return false;
        }

        if (current.wildcard) {
            int wildcardStart = wildcardStart(path, stringStart, current);
            if (wildcardStart < 0) {
                pathParameters.clear();
                return false;
            }
            pathParameters.put("*", path.substring(wildcardStart));
            return true;
        }
        if (current.parameter) {
            if (stringStart == i) {
                pathParameters.clear();
                return false;
            }
            pathParameters.put(current.value, path.substring(stringStart, i));
            return true;
        }
        if (segmentNotEquals(path, stringStart, i, current.value)) {
            pathParameters.clear();
            return false;
        }
        return true;
    }

    private static boolean segmentNotEquals(String path, int start, int end, String expected) {
        int length = end - start;
        return expected.length() != length || !path.regionMatches(start, expected, 0, length);
    }

    private static int wildcardStart(String path, int segmentStart, Part wildcard) {
        int prefixLength = wildcard.value.length() - 1;
        return path.regionMatches(segmentStart, wildcard.value, 0, prefixLength)
            ? segmentStart + prefixLength
            : -1;
    }

    String templateString() {
        return this.templateString;
    }

    String base() {
        return this.base;
    }

    Set<String> parameterNames() {
        return this.parameterNames;
    }

    @Nullable
    String wildcardPrefix() {
        return this.wildcardPrefix;
    }

    int parameterCount() {
        return this.parameterCount;
    }

    boolean exact() {
        return !this.template && this.wildcardPrefix == null;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof RadixPathTemplate that && this.compareTo(that) == 0;
    }

    @Override
    public int hashCode() {
        return this.hashCode;
    }

    private int calculateHashCode() {
        int result = 31 * this.base.hashCode() + Boolean.hashCode(this.template);
        if (!this.template) {
            return result;
        }
        result = 31 * result + Boolean.hashCode(this.trailingSlash);
        for (var part : this.parts) {
            result = 31 * result + (part.parameter ? 1 : part.value.hashCode());
        }
        return result;
    }

    @Override
    public int compareTo(final RadixPathTemplate o) {
        if (this.template && !o.template) {
            return 1;
        } else if (o.template && !this.template) {
            return -1;
        }

        int res = this.base.compareTo(o.base);
        if (res > 0) {
            return -1;
        } else if (res < 0) {
            return 1;
        } else if (!this.template) {
            return 0;
        }

        int i = 0;
        for (; ; ) {
            if (this.parts.length == i) {
                if (o.parts.length == i) {
                    if (this.trailingSlash == o.trailingSlash) {
                        return this.base.compareTo(o.base);
                    }
                    return this.trailingSlash ? -1 : 1;
                }
                return 1;
            } else if (o.parts.length == i) {
                return -1;
            }
            var thisPath = this.parts[i];
            var otherPart = o.parts[i];
            if (thisPath.parameter && !otherPart.parameter) {
                return 1;
            } else if (!thisPath.parameter && otherPart.parameter) {
                return -1;
            } else if (!thisPath.parameter) {
                int partResult = thisPath.value.compareTo(otherPart.value);
                if (partResult != 0) {
                    return partResult;
                }
            }
            i++;
        }
    }

    private static final class Part {
        private final boolean parameter;
        private final boolean wildcard;
        private final String value;

        private Part(boolean parameter, boolean wildcard, String value) {
            this.parameter = parameter;
            this.wildcard = wildcard;
            this.value = value;
        }

        private static Part parameter(String value) {
            return new Part(true, false, value);
        }

        private static Part literal(String value) {
            return new Part(false, value.endsWith("*"), value);
        }
    }

    private static final class TwoParameterMap extends AbstractMap<String, String> {
        private final String firstKey;
        private final String firstValue;
        private final String secondKey;
        private final String secondValue;

        private TwoParameterMap(String firstKey, String firstValue, String secondKey, String secondValue) {
            this.firstKey = firstKey;
            this.firstValue = firstValue;
            this.secondKey = secondKey;
            this.secondValue = secondValue;
        }

        @Override
        public String get(Object key) {
            if (this.firstKey.equals(key)) {
                return this.firstValue;
            }
            return this.secondKey.equals(key) ? this.secondValue : null;
        }

        @Override
        public boolean containsKey(Object key) {
            return this.firstKey.equals(key) || this.secondKey.equals(key);
        }

        @Override
        public int size() {
            return 2;
        }

        @Override
        public Set<Entry<String, String>> entrySet() {
            return new AbstractSet<>() {
                @Override
                public Iterator<Entry<String, String>> iterator() {
                    return new Iterator<>() {
                        private int index;

                        @Override
                        public boolean hasNext() {
                            return this.index < 2;
                        }

                        @Override
                        public Entry<String, String> next() {
                            return switch (this.index++) {
                                case 0 -> Map.entry(firstKey, firstValue);
                                case 1 -> Map.entry(secondKey, secondValue);
                                default -> throw new NoSuchElementException();
                            };
                        }
                    };
                }

                @Override
                public int size() {
                    return 2;
                }
            };
        }
    }
}
