package io.koraframework.http.server.common.router;

import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Parsed path template optimized for {@link OptimizedOriginalPathTemplateMatcher}.
 *
 * <h2>Parsing and retained representation</h2>
 * <ol>
 *   <li>Normalize the input path and parse it with a single character-level state machine.</li>
 *   <li>Retain the static base, ordered suffix {@code Part} list, trailing-slash flag, wildcard
 *       prefix, parameter-name set, and precomputed parameter count.</li>
 *   <li>Precompute the semantic hash code so registration and duplicate detection do not need to
 *       walk the template repeatedly.</li>
 * </ol>
 *
 * <h2>Matching algorithm</h2>
 * <p>The generic matcher scans the suffix once, comparing literal regions without creating
 * temporary segment strings and writing captures into the caller's map. The
 * {@code baseMatched} variant skips the prefix test when the enclosing trie has already verified
 * it. A dedicated single-parameter path records integer capture offsets first and creates the
 * substring and singleton map only after every literal and trailing-slash condition succeeds.</p>
 *
 * <h2>Ordering and equivalence</h2>
 * <p>Natural ordering defines candidate priority: exact before dynamic, literal before parameter,
 * and more specific template shapes before less specific ones. Parameter names do not affect
 * semantic equivalence, allowing registration to detect routes that match the same set of paths.</p>
 */
final class OptimizedOriginalPathTemplate implements Comparable<OptimizedOriginalPathTemplate> {

    private final String templateString;
    private final boolean template;
    private final String base;
    private final List<Part> parts;
    private final Set<String> parameterNames;
    private final boolean trailingSlash;
    private final @Nullable String wildcardPrefix;
    private final int parameterCount;
    private final int hashCode;

    private OptimizedOriginalPathTemplate(String templateString,
                                          boolean template,
                                          String base,
                                          List<Part> parts,
                                          Set<String> parameterNames,
                                          boolean trailingSlash,
                                          @Nullable String wildcardPrefix,
                                          int parameterCount) {
        this.templateString = templateString;
        this.template = template;
        this.base = base;
        this.parts = parts;
        this.parameterNames = parameterNames;
        this.trailingSlash = trailingSlash;
        this.wildcardPrefix = wildcardPrefix;
        this.parameterCount = parameterCount;
        this.hashCode = this.calculateHashCode();
    }

    @SuppressWarnings("fallthrough")
    public static OptimizedOriginalPathTemplate create(final String inputPath) {
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
        int state = 0;
        String base = "";
        ArrayList<Part> parts = null;
        int stringStart = 0;
        int parameterCount = 0;

        // 0 parsing base
        // 1 parsing base, last char was /
        // 2 in template part
        // 3 just after template part, expecting /
        // 4 expecting either template or segment
        // 5 in segment
        for (int i = 0; i < path.length(); ++i) {
            final char c = path.charAt(i);
            switch (state) {
                case 0: {
                    if (c == '/') {
                        state = 1;
                    } else if (c == '*') {
                        base = path.substring(0, i + 1);
                        stringStart = i;
                        state = 5;
                    }
                    break;
                }
                case 1: {
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
                    break;
                }
                case 2: {
                    if (c == '}') {
                        if (parts == null) {
                            parts = new ArrayList<>(4);
                        }
                        parts.add(Part.parameter(path.substring(stringStart, i)));
                        parameterCount++;
                        stringStart = i;
                        state = 3;
                    }
                    break;
                }
                case 3: {
                    if (c == '/') {
                        state = 4;
                    } else {
                        throw parseException(path);
                    }
                    break;
                }
                case 4: {
                    if (c == '{') {
                        stringStart = i + 1;
                        state = 2;
                    } else if (c != '/') {
                        stringStart = i;
                        state = 5;
                    }
                    break;
                }
                case 5: {
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
                    break;
                }
                default:
                    throw new IllegalStateException();
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

        final List<Part> partArray = parts == null ? Collections.emptyList() : parts;
        final Set<String> parameterNames;
        if (parameterCount == 0) {
            parameterNames = Set.of();
        } else {
            var names = new HashSet<String>(parameterCount);
            for (var part : partArray) {
                if (part.parameter) {
                    names.add(part.value);
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
        return new OptimizedOriginalPathTemplate(path, templatePath, base, partArray, parameterNames, trailingSlash, wildcardPrefix, parameterCount);
    }

    private static IllegalArgumentException parseException(String path) {
        return new IllegalArgumentException("Could not parse URI template %s, exception at char %s".formatted(path, path.length()));
    }

    /**
     * Checks whether path matches and writes captured parameters to supplied map.
     */
    public boolean matches(final String path, final Map<String, String> pathParameters) {
        return this.matches(path, pathParameters, false);
    }

    /**
     * Fast path for the overwhelmingly common single-parameter template.
     * Captured string and map are allocated only after the complete template matches.
     */
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
        Part current = this.parts.get(0);
        int stringStart = this.base.length();
        int captureStart = -1;
        int captureEnd = -1;
        String captureName = null;
        int i;
        for (i = stringStart; i < path.length(); i++) {
            if (path.charAt(i) != '/') {
                continue;
            }
            if (current.parameter) {
                captureStart = stringStart;
                captureEnd = i;
                captureName = current.value;
            } else if (segmentNotEquals(path, stringStart, i, current.value)) {
                return null;
            }
            currentPartPosition++;
            if (currentPartPosition == this.parts.size()) {
                if (!this.trailingSlash || i != path.length() - 1) {
                    return null;
                }
                return captureName == null ? null : Map.of(captureName, path.substring(captureStart, captureEnd));
            }
            current = this.parts.get(currentPartPosition);
            stringStart = i + 1;
        }
        if (currentPartPosition + 1 != this.parts.size()) {
            return null;
        }

        if (current.parameter) {
            if (stringStart == i) {
                return null;
            }
            captureStart = stringStart;
            captureEnd = i;
            captureName = current.value;
        } else if (segmentNotEquals(path, stringStart, i, current.value)) {
            return null;
        }
        return captureName == null ? null : Map.of(captureName, path.substring(captureStart, captureEnd));
    }

    /**
     * Matcher variant used when caller already matched {@link #base()}.
     */
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
        Part current = this.parts.get(0);
        int stringStart = baseLength;
        int i;
        for (i = baseLength; i < path.length(); ++i) {
            final char currentChar = path.charAt(i);
            if (current.wildcard) {
                break;
            }
            if (currentChar == '/') {
                if (current.parameter) {
                    pathParameters.put(current.value, path.substring(stringStart, i));
                } else if (segmentNotEquals(path, stringStart, i, current.value)) {
                    pathParameters.clear();
                    return false;
                }
                currentPartPosition++;
                if (currentPartPosition == this.parts.size()) {
                    return this.trailingSlash && i == path.length() - 1;
                }
                current = this.parts.get(currentPartPosition);
                stringStart = i + 1;
            }
        }
        if (currentPartPosition + 1 != this.parts.size()) {
            pathParameters.clear();
            return false;
        }

        if (current.wildcard) {
            pathParameters.put("*", path.substring(stringStart));
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

    String templateString() {
        return this.templateString;
    }

    boolean template() {
        return this.template;
    }

    String base() {
        return this.base;
    }

    Set<String> parameterNames() {
        return this.parameterNames;
    }

    boolean trailingSlash() {
        return this.trailingSlash;
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
        return this == o || o instanceof OptimizedOriginalPathTemplate that && this.compareTo(that) == 0;
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
    public int compareTo(final OptimizedOriginalPathTemplate o) {
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
            if (this.parts.size() == i) {
                if (o.parts.size() == i) {
                    if (this.trailingSlash == o.trailingSlash) {
                        return this.base.compareTo(o.base);
                    }
                    return this.trailingSlash ? -1 : 1;
                }
                return 1;
            } else if (o.parts.size() == i) {
                return -1;
            }
            var thisPath = this.parts.get(i);
            var otherPart = o.parts.get(i);
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

    private record Part(boolean parameter, boolean wildcard, String value) {

        private static Part parameter(String value) {
            return new Part(true, false, value);
        }

        private static Part literal(String value) {
            return new Part(false, "*".equals(value), value);
        }
    }
}
