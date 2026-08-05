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
 * <p>A template accepts at most one {@code *}, only inside its final path segment. The wildcard
 * may have literal text on either side, for example {@code /files/static-*} or
 * {@code /files/*.js}. Prefix and suffix literals are anchored at the beginning of the remaining
 * path and the end of the complete request path respectively; {@code *} captures the characters
 * between them, including slashes and an empty remainder. Because the suffix is end-anchored,
 * matching never scans possible wildcard positions or backtracks. A wildcard without a suffix
 * keeps a dedicated trailing-catch-all fast path. Multiple wildcards, wildcards inside parameter
 * declarations, and wildcards in non-final segments are rejected while parsing so trie and
 * template matching cannot disagree.</p>
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
    private final @Nullable String wildcardSuffix;
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
                              @Nullable String wildcardSuffix,
                              int parameterCount,
                              String[] captureNames) {
        this.templateString = templateString;
        this.template = template;
        this.base = base;
        this.parts = parts;
        this.parameterNames = parameterNames;
        this.trailingSlash = trailingSlash;
        this.wildcardPrefix = wildcardPrefix;
        this.wildcardSuffix = wildcardSuffix;
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
                default -> throw new IllegalStateException("Kora internal error: unknown HTTP route parser state: " + state);
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
                throw new IllegalStateException("Kora internal error: unknown HTTP route parser final state: " + state);
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

        final int wildcardIndex = path.indexOf('*');
        final String wildcardSuffix = wildcardIndex < 0 ? null : path.substring(wildcardIndex + 1);
        final int baseWildcardIndex = base.indexOf('*');
        final String wildcardPrefix;
        final boolean templatePath;
        if (baseWildcardIndex < 0) {
            wildcardPrefix = null;
            templatePath = state > 1;
        } else {
            wildcardPrefix = base.substring(0, baseWildcardIndex);
            templatePath = true;
        }
        return new RadixPathTemplate(path, templatePath, base, partArray, parameterNames, trailingSlash,
            wildcardPrefix, wildcardSuffix, parameterCount, captureNames);
    }

    private static void validateWildcard(String path) {
        int wildcard = path.indexOf('*');
        if (wildcard < 0) {
            return;
        }
        int previousClosingBrace = path.lastIndexOf('}', wildcard);
        boolean insideParameter = path.lastIndexOf('{', wildcard) > previousClosingBrace;
        boolean multipleWildcards = path.indexOf('*', wildcard + 1) >= 0;
        boolean outsideFinalSegment = wildcard < path.lastIndexOf('/');
        if (insideParameter || multipleWildcards || outsideFinalSegment) {
            throw new IllegalArgumentException(
                "Wildcard '*' is only allowed once and in the final path segment: " + path
                    + ". Valid examples: /files/* and /files/*.js"
            );
        }
    }

    private static IllegalArgumentException parseException(String path) {
        return new IllegalArgumentException("""
            Could not parse HTTP route path template:
              %s
            Hint:
              Parameters must use '{name}' and must occupy a full path segment.
            Fix:
              Use paths like '/users/{id}' or '/files/*'. Do not mix parameter braces with literal text inside one segment.
            """.formatted(path));
    }

    @Nullable
    Map<String, String> matchSingleParameter(final String path, boolean baseMatched) {
        var wildcardPrefix = this.wildcardPrefix;
        if (wildcardPrefix != null) {
            if (!baseMatched && !path.startsWith(wildcardPrefix)) {
                return null;
            }
            int wildcardEnd = this.baseWildcardEnd(path);
            return wildcardEnd < 0
                ? null
                : Map.of("*", path.substring(wildcardPrefix.length(), wildcardEnd));
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
            long wildcardRange = wildcardRange(path, stringStart, current);
            if (wildcardRange < 0) {
                return null;
            }
            int wildcardStart = rangeStart(wildcardRange);
            int wildcardEnd = rangeEnd(wildcardRange);
            if (captured++ == 0) {
                firstStart = wildcardStart;
                firstEnd = wildcardEnd;
            } else {
                secondStart = wildcardStart;
                secondEnd = wildcardEnd;
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
            int wildcardEnd = this.baseWildcardEnd(path);
            if (wildcardEnd < 0) {
                return false;
            }
            pathParameters.put("*", path.substring(wildcardPrefix.length(), wildcardEnd));
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
            long wildcardRange = wildcardRange(path, stringStart, current);
            if (wildcardRange < 0) {
                pathParameters.clear();
                return false;
            }
            pathParameters.put("*", path.substring(rangeStart(wildcardRange), rangeEnd(wildcardRange)));
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

    private int baseWildcardEnd(String path) {
        var suffix = this.wildcardSuffix;
        int suffixLength = suffix == null ? 0 : suffix.length();
        int captureEnd = path.length() - suffixLength;
        if (captureEnd < this.wildcardPrefix.length()) {
            return -1;
        }
        return suffixLength == 0 || path.regionMatches(captureEnd, suffix, 0, suffixLength)
            ? captureEnd
            : -1;
    }

    private static long wildcardRange(String path, int segmentStart, Part wildcard) {
        int captureStart = segmentStart + wildcard.wildcardIndex;
        int suffixLength = wildcard.value.length() - wildcard.wildcardIndex - 1;
        int captureEnd = path.length() - suffixLength;
        if (captureEnd < captureStart
            || wildcard.wildcardIndex > 0
            && !path.regionMatches(segmentStart, wildcard.value, 0, wildcard.wildcardIndex)
            || suffixLength > 0
            && !path.regionMatches(captureEnd, wildcard.value, wildcard.wildcardIndex + 1, suffixLength)) {
            return -1;
        }
        return (long) captureStart << 32 | captureEnd & 0xffffffffL;
    }

    private static int rangeStart(long range) {
        return (int) (range >>> 32);
    }

    private static int rangeEnd(long range) {
        return (int) range;
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

    @Nullable
    String wildcardSuffix() {
        return this.wildcardSuffix;
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
                if (thisPath.wildcard && !otherPart.wildcard) {
                    return 1;
                } else if (!thisPath.wildcard && otherPart.wildcard) {
                    return -1;
                } else if (thisPath.wildcard) {
                    int literalLengthResult = Integer.compare(
                        otherPart.value.length(), thisPath.value.length()
                    );
                    if (literalLengthResult != 0) {
                        return literalLengthResult;
                    }
                }
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
        private final int wildcardIndex;
        private final String value;

        private Part(boolean parameter, int wildcardIndex, String value) {
            this.parameter = parameter;
            this.wildcard = wildcardIndex >= 0;
            this.wildcardIndex = wildcardIndex;
            this.value = value;
        }

        private static Part parameter(String value) {
            return new Part(true, -1, value);
        }

        private static Part literal(String value) {
            return new Part(false, value.indexOf('*'), value);
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
